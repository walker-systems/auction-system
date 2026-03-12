package com.walker.bidding.auction;

import com.walker.bidding.exception.ConcurrentBidException;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final Sinks.Many<Auction> globalFirehose = Sinks.many().multicast().directBestEffort();

    public Mono<Auction> placeBid(String auctionId, String bidder, BigDecimal bidAmount,
                                  String ipAddress, String userAgent, int reactionTimeMs) {

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
                                                        // 👇 NEW: Emit the successful bid to the firehose
                                                        .doOnSuccess(v -> globalFirehose.tryEmitNext(updatedAuction))
                                                        .thenReturn(updatedAuction);
                                            } else {
                                                return Mono.error(new ConcurrentBidException("Collision"));
                                            }
                                        });
                            });
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(50)).filter(t -> t instanceof ConcurrentBidException));
    }

    public Mono<Auction> placeMaxBid(String auctionId, String bidderId, BigDecimal maxBid,
                                     String ipAddress, String userAgent, int reactionTimeMs) {

        log.info("📥 Received Proxy Bid Request: User '{}' bidding up to ${} on {}", bidderId, maxBid, auctionId);

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
                });
    }
    public Flux<Auction> streamAuctionUpdates(String auctionId) {
        return auctionRepository.observeAuctionUpdates(auctionId).sample(Duration.ofMillis(100));
    }

    // 👇 FIXED: Instead of 10,000 DB calls, we just return the shared hot stream!
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
                        // 👇 NEW: Emit to firehose
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
                            // 👇 NEW: Emit to firehose
                            .doOnSuccess(v -> globalFirehose.tryEmitNext(closedAuction));
                }).subscribe();
    }
}
