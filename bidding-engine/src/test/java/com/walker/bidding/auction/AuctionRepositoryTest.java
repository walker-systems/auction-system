package com.walker.bidding.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootTest
@Testcontainers
public class AuctionRepositoryTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ReactiveStringRedisTemplate stringTemplate;

    @BeforeEach
    void setUp() {
        auctionRepository.deleteAll().block();
    }

    @Test
    void updateWithVersion_whenNewVersionGreaterByOne_shouldUpdateAndReturnTrue() {
        Auction startingAuction = new Auction(
                "test-1", "item-1", new BigDecimal("100.00"), "user1",
                Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        auctionRepository.save(startingAuction).block();

        Auction proposedAuction = new Auction(
                "test-1", "item-1", new BigDecimal("150.00"), "user2",
                startingAuction.endsAt(), true, 2,
                null, null, 0, 0, 0
        );

        StepVerifier.create(auctionRepository.updateWithVersion(proposedAuction))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void updateWithVersion_whenNewVersionNotGreaterByOne_shouldRejectAndReturnFalse() {
        Auction startingAuction = new Auction(
                "test-2", "item-2", new BigDecimal("100.00"), "user-1",
                Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        auctionRepository.save(startingAuction).block();

        Auction proposedAuction = new Auction(
                "test-2", "item-2", new BigDecimal("150.00"), "sneakyUser",
                startingAuction.endsAt(), true, 6,
                null, null, 0, 0, 0
        );

        StepVerifier.create(auctionRepository.updateWithVersion(proposedAuction))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void findAll_shouldRelyOnActiveIndex() {
        Auction activeAuction = new Auction(
                "test-active", "Active Item", BigDecimal.TEN, "User1",
                Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        Auction inactiveAuction = new Auction(
                "test-inactive", "Ended Item", BigDecimal.TEN, "User2",
                Instant.now().minusSeconds(3600), false, 1,
                null, null, 0, 0, 0
        );

        auctionRepository.save(activeAuction).block();
        auctionRepository.save(inactiveAuction).block();

        StepVerifier.create(auctionRepository.findAll())
                .expectNextMatches(a -> a.id().equals("test-active"))
                .verifyComplete();

        StepVerifier.create(stringTemplate.opsForSet().members("active_auctions"))
                .expectNext("test-active")
                .verifyComplete();
    }
}
