package com.walker.bidding.auction;

import com.walker.bidding.exception.ConcurrentBidException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    private final Sinks.Many<Auction> globalFirehose = Sinks.many().multicast().directBestEffort();

    private Timer proxyBidTimer;
    private Timer standardBidTimer;

    @PostConstruct
    public void initMetrics() {
        this.proxyBidTimer = Timer.builder("bids.processing")
                .tag("type", "proxy")
                .publishPercentiles(0.99)
                .register(meterRegistry);

        this.standardBidTimer = Timer.builder("bids.processing")
                .tag("type", "standard")
                .publishPercentiles(0.99)
                .register(meterRegistry);
    }

    public double getP99LatencyMs() {
        if (proxyBidTimer == null) return 0.0;
        var percentiles = proxyBidTimer.takeSnapshot().percentileValues();
        for (var p : percentiles) {
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
                            return Mono.error(new IllegalAccessException("User is banned."));
                        }

                        return auctionRepository.findById(auctionId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Auction not found.")))
                                .flatMap(auction -> {
                                    if (!auction.active()) return Mono.error(new IllegalStateException("Closed."));
                                    if (bidAmount.compareTo(auction.currentPrice()) <= 0) {
                                        return Mono.error(new IllegalArgumentException("Bid too low."));
                                    }

                                    Auction updatedAuction = new Auction(
                                            auction.id(), auction.itemId(), bidAmount, bidder,
                                            auction.endsAt(), true, auction.version() + 1,
                                            ipAddress, userAgent, reactionTimeMs,
                                            (reactionTimeMs < 100) ? 65 : 1, (reactionTimeMs < 100) ? 1 : 0
                                    );

                                    return auctionRepository.updateWithVersion(updatedAuction)
                                            .flatMap(bidSuccess -> {
                                                if (bidSuccess) {
                                                    log.info("✅ Bid placed by {} for ${}", bidder, bidAmount);
                                                    return auctionRepository.publishUpdate(updatedAuction)
                                                            .doOnSuccess(v -> globalFirehose.tryEmitNext(updatedAuction))
                                                            .thenReturn(updatedAuction);
                                                } else {
                                                    return Mono.error(new ConcurrentBidException("Collision"));
                                                }
                                            });
                                });
                    })
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(50)).filter(t -> t instanceof ConcurrentBidException))
                    .doFinally(signal -> sample.stop(standardBidTimer));
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
                            log.error("🚫 Access Denied: Banned user '{}' attempted proxy bid on {}", bidderId, auctionId);
                            return Mono.error(new IllegalAccessException("User banned."));
                        }

                        int bidCount = (reactionTimeMs < 100) ? 65 : 1;
                        int isNewIp = (reactionTimeMs < 100) ? 1 : 0;

                        return auctionRepository.placeProxyBid(auctionId, bidderId, maxBid, ipAddress, userAgent, reactionTimeMs, bidCount, isNewIp)
                                .flatMap(updatedAuction -> {
                                    log.info("📈 Proxy bid evaluated. Winner: {}, Visible Price: ${}",
                                            updatedAuction.highBidder(), updatedAuction.currentPrice());

                                    return auctionRepository.publishUpdate(updatedAuction)
                                            .doOnSuccess(v -> globalFirehose.tryEmitNext(updatedAuction))
                                            .thenReturn(updatedAuction);
                                });
                    })
                    .doFinally(signal -> sample.stop(proxyBidTimer));
        });
    }

    public Flux<Auction> streamAuctionUpdates(String auctionId) {
        return auctionRepository.observeAuctionUpdates(auctionId).sample(Duration.ofMillis(100));
    }

    public Flux<Auction> streamAllAuctionUpdates() {
        log.info("🔌 Client connected to the global firehose...");
        return globalFirehose.asFlux();
    }

    public Flux<Auction> getAllAuctions() {
        return auctionRepository.findAll();
    }

    public Mono<Void> revertFraudulentBid(String auctionId, String fraudUser) {
        return auctionRepository.revertFraudulentBid(auctionId, fraudUser, new BigDecimal("10.00"))
                .flatMap(reverted -> auctionRepository.publishUpdate(reverted)
                        .doOnSuccess(v -> globalFirehose.tryEmitNext(reverted)))
                .then();
    }

    @Scheduled(fixedRate = 60000)
    public void sweepExpiredAuctions() {
        auctionRepository.findAll()
                .filter(Auction::active)
                .filter(a -> a.endsAt().isBefore(Instant.now()))
                .flatMap(auction -> {
                    Auction closedAuction = new Auction(auction.id(), auction.itemId(), auction.currentPrice(), auction.highBidder(), auction.endsAt(), false, auction.version() + 1, auction.ipAddress(), auction.userAgent(), auction.reactionTimeMs(), auction.bidCountLastMin(), auction.isNewIp());
                    return auctionRepository.save(closedAuction)
                            .then(auctionRepository.publishUpdate(closedAuction))
                            .doOnSuccess(v -> globalFirehose.tryEmitNext(closedAuction));
                }).subscribe();
    }
}
