package com.walker.bidding.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walker.bidding.exception.BannedUserException;
import com.walker.bidding.exception.ConcurrentBidException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Range;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, BigDecimal> startingPrices = new ConcurrentHashMap<>();

    private Timer proxyBidTimer;
    private Timer standardBidTimer;

    private static final String METRIC_BIDS_PROCESSING = "bids.processing";

    @PostConstruct
    public void initMetrics() {
        this.proxyBidTimer = Timer.builder(METRIC_BIDS_PROCESSING)
                .tag("type", "proxy")
                .publishPercentiles(0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.standardBidTimer = Timer.builder(METRIC_BIDS_PROCESSING)
                .tag("type", "standard")
                .publishPercentiles(0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        loadStartingPrices();
    }

    private void loadStartingPrices() {
        try {
            InputStream iS = new ClassPathResource("auctions_catalog.json").getInputStream();
            List<Auction> catalog = objectMapper.readValue(iS, new TypeReference<>() {});
            for (Auction auction : catalog) {
                startingPrices.put(auction.id(), auction.currentPrice());
            }
            log.info("💰 Loaded {} starting prices from catalog for rollback safety.", startingPrices.size());
        } catch (Exception e) {
            log.error("Failed to load auctions_catalog.json for starting prices", e);
        }
    }

    public double getP99LatencyMs() {
        double proxyP99 = getTimerP99(proxyBidTimer);
        double standardP99 = getTimerP99(standardBidTimer);
        return Math.max(proxyP99, standardP99);
    }

    private double getTimerP99(Timer timer) {
        if (timer == null) return 0.0;
        ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
        for (ValueAtPercentile p : percentiles) {
            if (p.percentile() == 0.99) {
                return p.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0.0;
    }

    public Mono<Auction> placeBid(String auctionId, String bidder, BigDecimal bidAmount,
                                  String ipAddress, String userAgent, int reactionTimeMs) {

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            return redisTemplate.opsForSet().isMember("banned_users", bidder)
                    .flatMap(isBanned -> {
                        if (isBanned) {
                            return Mono.error(new BannedUserException("User is banned."));
                        }

                        return auctionRepository.findById(auctionId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Auction not found.")))
                                .flatMap(auction -> {
                                    if (!auction.active()) return Mono.error(new IllegalStateException("Closed."));
                                    if (bidAmount.compareTo(auction.currentPrice()) <= 0) {
                                        return Mono.error(new IllegalArgumentException("Bid too low."));
                                    }

                                    Instant newEndsAt = auction.endsAt();
                                    if (newEndsAt != null) {
                                        long timeLeftSec = Duration.between(Instant.now(), newEndsAt).getSeconds();
                                        if (timeLeftSec > 0 && timeLeftSec <= 5) {
                                            newEndsAt = Instant.now().plusSeconds(30);
                                            log.info("⏱️ SOFT CLOSE: Auction {} extended by 30 seconds", auctionId);
                                        }
                                    }

                                    Auction updatedAuction = new Auction(
                                            auction.id(), auction.itemId(), bidAmount, bidder,
                                            newEndsAt, true, auction.version() + 1,
                                            ipAddress, userAgent, reactionTimeMs,
                                            (reactionTimeMs < 100) ? 65 : 1, (reactionTimeMs < 100) ? 1 : 0
                                    );

                                    return auctionRepository.updateAuction(updatedAuction)
                                            .flatMap(bidSuccess -> {
                                                if (bidSuccess) {
                                                    log.info("✅ Bid placed by {} for ${}", bidder, bidAmount);
                                                    return auctionRepository.publishUpdate(updatedAuction)
                                                            .thenReturn(updatedAuction);
                                                } else {
                                                    return Mono.error(new ConcurrentBidException("Collision"));
                                                }
                                            });
                                });
                    })
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(50)).filter(t -> t instanceof ConcurrentBidException))
                    .doFinally(_ -> sample.stop(standardBidTimer));
        });
    }

    public Mono<Auction> placeMaxBid(String auctionId, String bidderId, BigDecimal maxBid,
                                     String ipAddress, String userAgent, int reactionTimeMs) {

        log.info("📥 Received Proxy Bid Request: User '{}' bidding up to ${} on {}", bidderId, maxBid, auctionId);

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            return redisTemplate.opsForSet().isMember("banned_users", bidderId)
                    .flatMap(isBanned -> {
                        if (isBanned) {
                            return Mono.error(new IllegalAccessException("User banned."));
                        }

                        return auctionRepository.findById(auctionId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Auction not found.")))
                                .flatMap(auction -> {
                                    if (!auction.active()) return Mono.error(new IllegalStateException("Closed."));

                                    BigDecimal minimumWinningBid = BidIncrementCalculator.getMinimumNextBid(auction.currentPrice());
                                    if (!bidderId.equals(auction.highBidder()) && maxBid.compareTo(minimumWinningBid) < 0) {
                                        return Mono.error(new IllegalArgumentException("Bid must be at least $" + minimumWinningBid));
                                    }

                                    String maxBidsKey = "auctions:" + auctionId + ":max_bids";

                                    return redisTemplate.opsForZSet().score(maxBidsKey, bidderId)
                                            .defaultIfEmpty(0.0)
                                            .flatMap(existingMax -> {
                                                if (maxBid.compareTo(BigDecimal.valueOf(existingMax)) <= 0) {
                                                    return Mono.error(new IllegalArgumentException("Max bid must be higher than your current max bid"));
                                                }

                                                return redisTemplate.opsForZSet().add(maxBidsKey, bidderId, maxBid.doubleValue())
                                                        .thenMany(redisTemplate.opsForZSet().reverseRangeWithScores(maxBidsKey, Range.closed(0L, 1L)))
                                                        .collectList()
                                                        .flatMap(top2 -> {
                                                            String highestBidder = top2.getFirst().getValue();
                                                            Double score0 = top2.getFirst().getScore();
                                                            BigDecimal highestMax = BigDecimal.valueOf(score0 != null ? score0 : 0.0);

                                                            BigDecimal newVisiblePrice = auction.currentPrice();

                                                            if (top2.size() > 1) {
                                                                Double score1 = top2.get(1).getScore();
                                                                BigDecimal secondMax = BigDecimal.valueOf(score1 != null ? score1 : 0.0);
                                                                BigDecimal increment = BidIncrementCalculator.getIncrement(secondMax);
                                                                BigDecimal secondMaxPlusIncrement = secondMax.add(increment);

                                                                if (secondMaxPlusIncrement.compareTo(highestMax) > 0) {
                                                                    newVisiblePrice = highestMax;
                                                                } else {
                                                                    newVisiblePrice = secondMaxPlusIncrement;
                                                                }
                                                            }
                                                            Instant newEndsAt = auction.endsAt();
                                                            if (newEndsAt != null) {
                                                                long timeLeftSec = Duration.between(Instant.now(), newEndsAt).getSeconds();
                                                                if (timeLeftSec > 0 && timeLeftSec <= 5) {
                                                                    newEndsAt = Instant.now().plusSeconds(30);
                                                                    log.info("⏱️ SOFT CLOSE: Auction {} extended by 30 seconds!", auctionId);
                                                                }
                                                            }

                                                            int bidCount = (reactionTimeMs < 100) ? 65 : 1;
                                                            int isNewIp = (reactionTimeMs < 100) ? 1 : 0;

                                                            Auction updatedAuction = new Auction(
                                                                    auction.id(), auction.itemId(), newVisiblePrice, highestBidder,
                                                                    newEndsAt, true, auction.version() + 1,
                                                                    ipAddress, userAgent, reactionTimeMs, bidCount, isNewIp
                                                            );

                                                            return auctionRepository.updateAuction(updatedAuction)
                                                                    .flatMap(bidSuccess -> {
                                                                        if (bidSuccess) {
                                                                            log.info("📈 Proxy bid evaluated. Winner: {}, Visible Price: ${}",
                                                                                    updatedAuction.highBidder(), updatedAuction.currentPrice());
                                                                            return auctionRepository.publishUpdate(updatedAuction)
                                                                                    .thenReturn(updatedAuction);
                                                                        } else {
                                                                            return Mono.error(new ConcurrentBidException("Collision"));
                                                                        }
                                                                    });
                                                        });
                                            });
                                });
                    })
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(50)).filter(t -> t instanceof ConcurrentBidException))
                    .doFinally(_ -> sample.stop(proxyBidTimer));
        });
    }

    public Flux<Auction> streamAuctionUpdates(String auctionId) {
        return auctionRepository.observeAuctionUpdates(auctionId).sample(Duration.ofMillis(100));
    }

    public Flux<Auction> streamAllAuctionUpdates() {
        log.info("🔌 Client connected to the Redis global firehose...");
        return auctionRepository.observeAllAuctionUpdates();
    }

    public Flux<Auction> getAllAuctions() {
        return auctionRepository.findAll();
    }

    public Mono<Void> revertBid(String auctionId, String fraudUser) {
        BigDecimal trueStartingPrice = startingPrices.getOrDefault(auctionId, new BigDecimal("10.00"));

        return auctionRepository.revertBid(auctionId, fraudUser, trueStartingPrice)
                .flatMap(auction -> {
                    log.warn("🔨 AUCTION REVERTED: {} reset to ${} after banning {}", auctionId, auction.currentPrice(), fraudUser);
                    return auctionRepository.publishUpdate(auction);
                })
                .then();
    }

    private Flux<String> getExpiredAuctionIds() {
        double now = (double) Instant.now().toEpochMilli();
        return redisTemplate.opsForZSet()
                .rangeByScore("auction:expirations", Range.closed(0.0, now));
    }

    @Scheduled(fixedRate = 600000)
    public void sweepExpiredAuctions() {
        getExpiredAuctionIds()
                .flatMap(auctionRepository::findById)
                .filter(Auction::active)
                .filter(a -> a.endsAt().isBefore(Instant.now()))
                .flatMap(auction -> {
                    Auction closedAuction = new Auction(auction.id(), auction.itemId(), auction.currentPrice(),
                            auction.highBidder(), auction.endsAt(), false,
                            auction.version() + 1, auction.ipAddress(), auction.userAgent(),
                            auction.reactionTimeMs(), auction.bidCountLastMin(), auction.isNewIp());
                    return auctionRepository.save(closedAuction)
                            .then(redisTemplate.opsForZSet().remove("auction:expirations", auction.id()))
                            .then(auctionRepository.publishUpdate(closedAuction))
                            .thenReturn(closedAuction);
                }).subscribe(
                        auction -> log.info("Successfully closed expired auction: {}", auction.id()),
                        err -> log.error("Error sweeping auctions", err)
                );
    }

    @Scheduled(initialDelay = 60000, fixedRate = 300000)
    public void restockLowInventory() {
        redisTemplate.opsForSet().size("active_auctions")
                .flatMap(activeCount -> {
                    // If there are less than 50 active auctions left, inject 200 fresh ones
                    if (activeCount < 50) {
                        log.info("📉 Low inventory detected ({} active). Restocking 200 fresh auctions...", activeCount);

                        return Flux.range(1, 200)
                                .flatMap(i -> {
                                    // Generate restock id to avoid collisions with older auctions
                                    String newId = "auc-restock-" + System.currentTimeMillis() + "-" + i;

                                    // Grab a random starting price from cached catalog (or default to $10)
                                    BigDecimal startPrice = startingPrices.values().stream()
                                            .skip(ThreadLocalRandom.current().nextInt(!startingPrices.isEmpty() ? startingPrices.size() : 1))
                                            .findFirst()
                                            .orElse(new BigDecimal("10.00"));

                                    // Set expiration between 15 minutes and 2 hours from now
                                    long offsetSeconds = ThreadLocalRandom.current().nextLong(900, 7200);
                                    Instant endsAt = Instant.now().plusSeconds(offsetSeconds);

                                    Auction freshAuction = new Auction(
                                            newId, "item-restock-" + i, startPrice, "System", endsAt,
                                            true, 0, null, null, 0, 0, 0
                                    );

                                    return auctionRepository.save(freshAuction)
                                            .then(auctionRepository.publishUpdate(freshAuction));
                                }, 10) // Process 10 at a time to be gentle on the droplet
                                .then(Mono.just(true));
                    }
                    return Mono.just(false);
                })
                .subscribe(
                        restocked -> {
                            if (restocked) {
                                log.info("✅ Restock complete. The ecosystem lives on.");
                            }
                        },
                        err -> log.error("❌ Error restocking auctions", err)
                );
    }
}
