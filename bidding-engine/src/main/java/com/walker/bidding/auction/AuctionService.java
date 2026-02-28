package com.walker.bidding.auction;

import com.walker.bidding.exception.ConcurrentBidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ConcurrentModificationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private final AuctionRepository auctionRepository;

    public Mono<Auction> placeBid(String auctionId, String bidder, BigDecimal bidAmount) {

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

                    Auction updatedAuction = new Auction(
                            auction.id(),
                            auction.itemId(),
                            bidAmount,
                            bidder,
                            auction.endsAt(),
                            auction.active(),
                            auction.version() + 1
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
                })

                // Retry from the beginning (findById...) in case of bid collision error (3 additional tries, then 409)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
                        .filter(throwable -> throwable instanceof ConcurrentBidException)
                );
    }

    public Flux<Auction> streamAuctionUpdates(String auctionId) {
        return auctionRepository.observeAuctionUpdates()
                .filter(auction -> auction.id().equals(auctionId));
    }

    public Flux<Auction> getAllAuctions() {
        return auctionRepository.findAll();
    }

    public Mono<Void> revertFraudulentBid(String auctionId, String fraudulentUser) {
        return auctionRepository.findById(auctionId)
                .flatMap(auction -> {
                    if (fraudulentUser.equals(auction.highBidder())) {
                        log.warn("⏪ Reverting fraudulent bid on {} by {}", auctionId, fraudulentUser);

                        BigDecimal revertedPrice = auction.currentPrice().subtract(new BigDecimal("10.00"));

                        Auction revertedAuction = new Auction(
                                auction.id(),
                                auction.itemId(),
                                revertedPrice,
                                "System", // Reset to default
                                auction.endsAt(),
                                auction.active(),
                                auction.version() + 1 // Increment version so the update succeeds
                        );

                        // Save the fix, then publish the update so the UI flashes
                        return auctionRepository.updateWithVersion(revertedAuction)
                                .filter(Boolean::booleanValue)
                                .flatMap(success -> auctionRepository.publishUpdate(revertedAuction));
                    }
                    return Mono.empty();
                }).then();
    }
}
