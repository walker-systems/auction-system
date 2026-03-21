package com.walker.bidding.auction;

import com.walker.bidding.config.DatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;


@SpringBootTest
@Testcontainers
class AuctionServiceIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis/redis-stack:latest")
            .withExposedPorts(6379);

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @MockitoBean
    private DatabaseInitializer databaseInitializer;

    @Test
    void testProxyBidding_outbidsCompetitor_andIncrementsPrice() {
        String testAuctionId = "test-auc-999";
        String testItemName = "test-item-1";

        Auction newAuction = new Auction(
                testAuctionId, testItemName, new BigDecimal("10.00"),
                "System", Instant.now().plus(Duration.ofSeconds(100)),
                true, 0, null, null,
                0, 0, 0
        );

        Mono<Auction> bidProcess = auctionRepository.save(newAuction)
                .then(auctionService.placeMaxBid(
                        testAuctionId, "alice", new BigDecimal("50.00"), "127.0.0.1", "TestAgent", 500))
                .then(auctionService.placeMaxBid(
                        testAuctionId, "bob", new BigDecimal("75.00"), "127.0.0.1", "TestAgent", 500));

        StepVerifier.create(bidProcess)
                .expectNextMatches(auction ->
                        auction.highBidder().equals("bob") &&
                                auction.currentPrice().compareTo(new BigDecimal("55.00")) == 0)
                .verifyComplete();
    }

    @Test
    void testSoftClose_extendsAuctionEndTime_whenBidPlacedNearEnd() {
        String testAuctionId = "test-auc-softclose";

        Instant originalEndTime = Instant.now().plus(Duration.ofSeconds(4));

        Auction newAuction = new Auction(
                testAuctionId, "Sniper Bait",
                new BigDecimal("10.00"),
                "System", originalEndTime,
                true, 0, null, null,
                0, 0, 0
        );

        Mono<Auction> bidProcess = auctionRepository.save(newAuction)
                .then(auctionService.placeMaxBid(
                        testAuctionId, "sniper_sam", new BigDecimal("50.00"), "127.0.0.1", "TestAgent", 500));

        StepVerifier.create(bidProcess)
                .expectNextMatches(auction ->
                        auction.highBidder().equals("sniper_sam") &&
                                auction.endsAt().isAfter(originalEndTime.plusSeconds(10))
                )
                .verifyComplete();
    }

    @Test
    void testThunderingHerd_50ConcurrentBids_shouldMaintainConsistency() {
        String auctionId = "test-auc-herd";
        Auction auction = new Auction(auctionId, "item-99", new BigDecimal("10.00"), "System",
                Instant.now().plusSeconds(600), true, 0, "127.0.0.1", "TestAgent", 50, 0, 0);
        auctionRepository.save(auction).block();

        // Thundering Herd: 50 bids fired at the exact same microsecond
        // Capture only the bids that successfully survive the lock and are not outbid
        java.util.List<Auction> successfulBids = reactor.core.publisher.Flux.range(1, 50)
                .flatMap(i -> auctionService.placeBid(
                                auctionId,
                                "user_" + i,
                                new BigDecimal("10.00").add(BigDecimal.valueOf(i)),
                                "127.0.0.1",
                                "Agent",
                                150)
                        .onErrorResume(e -> reactor.core.publisher.Mono.empty()) // Drop bids that exhaust retries or are too low
                )
                .collectList()
                .block();

        assert successfulBids != null && !successfulBids.isEmpty() : "At least one bid should have succeeded";

        // Find the highest bid that successfully committed to Redis
        Auction expectedWinner = successfulBids.stream()
                .max(java.util.Comparator.comparing(Auction::currentPrice))
                .orElseThrow();

        // Verify the final state strictly matches the highest successful bid,
        // proving no data corruption, no lost updates, and accurate version counting.
        reactor.test.StepVerifier.create(auctionRepository.findById(auctionId))
                .expectNextMatches(a -> a.currentPrice().compareTo(expectedWinner.currentPrice()) == 0
                        && a.highBidder().equals(expectedWinner.highBidder())
                        && a.version() == successfulBids.size())
                .verifyComplete();
    }
    @Test
    void testRevertBid_shouldRestorePreviousHighestBidder() {
        String auctionId = "test-auc-rollback";
        Auction auction = new Auction(auctionId, "item-100", new BigDecimal("10.00"), "System",
                Instant.now().plusSeconds(600), true, 0, "127.0.0.1", "TestAgent", 50, 0, 0);
        auctionRepository.save(auction).block();

        // 1. Place a valid proxy bid (Max $30)
        auctionService.placeMaxBid(auctionId, "valid_user", new BigDecimal("30.00"), "127.0.0.1", "Agent", 150).block();

        // 2. Place a fraudulent proxy bid that outbids the valid user (Max $500)
        auctionService.placeMaxBid(auctionId, "fraud_bot", new BigDecimal("500.00"), "127.0.0.1", "Agent", 10).block();

        // 3. Trigger the Rollback (Simulating Sentinel catching the bot)
        StepVerifier.create(auctionService.revertBid(auctionId, "fraud_bot"))
                .verifyComplete();

        // 4. Verify the auction successfully kicked out the bot and restored the valid user
        StepVerifier.create(auctionRepository.findById(auctionId))
                .expectNextMatches(a -> a.highBidder().equals("valid_user"))
                .verifyComplete();
    }
}
