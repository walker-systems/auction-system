package com.walker.bidding.auction;

import com.walker.bidding.exception.ConcurrentBidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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

    public Mono<Auction> placeBid(String auctionId, String bidder, BigDecimal bidAmount,
                                  String ipAddress, String userAgent, int reactionTimeMs) {

        return redisTemplate.opsForSet().isMember("banned_users", bidder)
                .flatMap(isBanned -> {
                    if (isBanned) {
                        log.error("🚫 Access Denied: Banned user '{}' attempted to bid on auction {}", bidder, auctionId);
                        return Mono.error(new IllegalAccessException("User is banned for fraudulent activity."));
                    }

                    return auctionRepository.findById(auctionId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Auction not found: " + auctionId)))
                            .flatMap(auction -> {

                                if (!auction.active()) {
                                    return Mono.error(new IllegalStateException("Auction is closed."));
                                }
                                if (bidAmount.compareTo(auction.currentPrice()) <= 0) {
                                    return Mono.error(new IllegalArgumentException("Bid must be higher than the current price of "
                                            + auction.currentPrice()));
                                }

                                int simulatedBidCount = (reactionTimeMs < 100) ? 65 : 1;
                                int simulatedNewIp = (reactionTimeMs < 100) ? 1 : 0;

                                Auction updatedAuction = new Auction(
                                        auction.id(),
                                        auction.itemId(),
                                        bidAmount,
                                        bidder,
                                        auction.endsAt(),
                                        true,
                                        auction.version() + 1,
                                        ipAddress,
                                        userAgent,
                                        reactionTimeMs,
                                        simulatedBidCount,
                                        simulatedNewIp
                                );
                                return auctionRepository.updateWithVersion(updatedAuction)
                                        .flatMap(bidSuccess -> {
                                            if (bidSuccess) {
                                                log.info("✅ Bid placed successfully by {} for ${}", bidder, bidAmount);
                                                return auctionRepository.publishUpdate(updatedAuction)
                                                        .thenReturn(updatedAuction);
                                            } else {
                                                log.warn("⚠️ Collision detected for auction "
                                                        + "{}. Someone else bid at the exact same time!", auctionId);
                                                return Mono.error(new ConcurrentBidException("Bid collision"));
                                            }
                                        });
                            });
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
                        .filter(throwable -> throwable instanceof ConcurrentBidException)
                );
    }

    public Mono<Auction> placeMaxBid(String auctionId, String bidderId, BigDecimal maxBid,
                                     String ipAddress, String userAgent, int reactionTimeMs) {

        return redisTemplate.opsForSet().isMember("banned_users", bidderId)
                .flatMap(isBanned -> {
                    if (isBanned) {
                        log.error("🚫 Access Denied: Banned user '{}' attempted proxy bid on {}", bidderId, auctionId);
                        return Mono.error(new IllegalAccessException("User is banned for fraudulent activity."));
                    }

                    int simulatedBidCount = (reactionTimeMs < 100) ? 65 : 1;
                    int simulatedNewIp = (reactionTimeMs < 100) ? 1 : 0;

                    return auctionRepository.placeProxyBid(
                                    auctionId, bidderId, maxBid,
                                    ipAddress, userAgent, reactionTimeMs,
                                    simulatedBidCount, simulatedNewIp
                            )
                            .flatMap(updatedAuction -> {
                                log.info("📈 Proxy bid evaluated. Winner: {}, Visible Price: ${}",
                                        updatedAuction.highBidder(), updatedAuction.currentPrice());
                                return auctionRepository.publishUpdate(updatedAuction)
                                        .thenReturn(updatedAuction);
                            });
                });
    }

    public Flux<Auction> streamAuctionUpdates(String auctionId) {
        return auctionRepository.observeAuctionUpdates(auctionId)
                .sample(Duration.ofMillis(100));
    }

    public Flux<Auction> getAllAuctions() {
        return auctionRepository.findAll();
    }

    public Mono<Void> revertFraudulentBid(String auctionId, String fraudUser) {
        log.warn("⏪ Sentinel requested rollback for bot: {} on auction: {}", fraudUser, auctionId);

        BigDecimal defaultStartingPrice = new BigDecimal("10.00");

        return auctionRepository.revertFraudulentBid(auctionId, fraudUser, defaultStartingPrice)
                .flatMap(revertedAuction -> {
                    log.info("✅ Rollback complete. True winner restored: {} at ${}",
                            revertedAuction.highBidder(), revertedAuction.currentPrice());
                    return auctionRepository.publishUpdate(revertedAuction);
                })
                .then();
    }

    @Scheduled(fixedRate = 1000)
    public void sweepExpiredAuctions() {
        auctionRepository.findAll()
                .filter(Auction::active)
                .filter(auction -> auction.endsAt().isBefore(Instant.now()))
                .flatMap(auction -> {
                    log.info("⏰ Auction {} has ended. Closing and removing from storefront.", auction.id());

                    Auction closedAuction = new Auction(
                            auction.id(),
                            auction.itemId(),
                            auction.currentPrice(),
                            auction.highBidder(),
                            auction.endsAt(),
                            false, // 👈 Set active to false
                            auction.version() + 1,
                            auction.ipAddress(),
                            auction.userAgent(),
                            auction.reactionTimeMs(),
                            auction.bidCountLastMin(),
                            auction.isNewIp()
                    );

                    return auctionRepository.save(closedAuction)
                            .then(auctionRepository.publishUpdate(closedAuction));
                })
                .subscribe();
    }
}
