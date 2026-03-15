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
}
