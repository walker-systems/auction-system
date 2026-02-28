package com.walker.bidding.auction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    @Test
    void updateWithVersion_whenNewVersionGreaterByOne_shouldUpdateAndReturnTrue() {
        Auction startingAuction = new Auction(
                "test-1",
                "item-1",
                new BigDecimal("100.00"),
                "user1",
                Instant.now().plusSeconds(3600),
                true,
                1
        );

        auctionRepository.save(startingAuction).block();

        Auction proposedAuction = new Auction(
                "test-1",
                "item-1",
                new BigDecimal("150.00"),
                "user2",
                startingAuction.endsAt(),
                true,
                2 // proposedAuction.version - 1 == startingAuction.version
        );

        StepVerifier.create(auctionRepository.updateWithVersion(proposedAuction))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void updateWithVersion_whenNewVersionNotGreaterByOne_shouldRejectAndReturnFalse() {
        Auction startingAuction = new Auction(
                "test-2",
                "item-2",
                new BigDecimal("100.00"),
                "user-1",
                Instant.now().plusSeconds(3600),
                true,
                1
        );

        auctionRepository.save(startingAuction).block();

        Auction proposedAuction = new Auction(
                "test-2",
                "item-2",
                new BigDecimal("150.00"),
                "sneakyUser",
                startingAuction.endsAt(),
                true,
                6 // version too large
        );

        StepVerifier.create(auctionRepository.updateWithVersion(proposedAuction))
                .expectNext(false)
                .verifyComplete();
    }
}
