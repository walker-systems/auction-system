package com.walker.bidding.auction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    @InjectMocks
    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(auctionRepository.publishUpdate(any(Auction.class)))
                .thenReturn(Mono.just(1L));

        // Mock the AI Sentinel Ban List Check
        Mockito.lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        Mockito.lenient().when(setOperations.isMember(eq("banned_users"), any(String.class)))
                .thenReturn(Mono.just(false));
    }

    @Test
    void placeBid_whenValid_shouldUpdateAndReturnNewAuction() {
        String auctionId = "testAuction";
        Auction auction = new Auction(
                auctionId, "testItem", new BigDecimal("100.00"),
                "testUserA", Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        Mockito.when(auctionRepository.findById(auctionId)).thenReturn(Mono.just(auction));
        Mockito.when(auctionRepository.updateWithVersion(any(Auction.class))).thenReturn(Mono.just(true));

        Mono<Auction> validBidMono = auctionService.placeBid(
                auctionId, "testUserB", new BigDecimal("115.00"),
                "127.0.0.1", "test-agent", 100
        );

        StepVerifier.create(validBidMono)
                .assertNext(updatedAuction -> {
                    Assertions.assertEquals(new BigDecimal("115.00"), updatedAuction.currentPrice());
                    Assertions.assertEquals("testUserB", updatedAuction.highBidder());
                    Assertions.assertEquals(2, updatedAuction.version());
                })
                .verifyComplete();
    }

    @Test
    void placeBid_whenBidTooLow_shouldThrowError() {
        String auctionId = "testAuction";
        Auction auction = new Auction(
                auctionId, "testItem", new BigDecimal("100.00"),
                "testUserA", Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        Mockito.when(auctionRepository.findById(auctionId)).thenReturn(Mono.just(auction));

        Mono<Auction> lowBidMono = auctionService.placeBid(
                auctionId, "testUserB", new BigDecimal("50.00"),
                "127.0.0.1", "test-agent", 100
        );

        StepVerifier.create(lowBidMono).verifyError(IllegalArgumentException.class);
    }

    @Test
    void placeBid_whenCollisionOccurs_shouldRetryAndSucceed() {
        String auctionId = "testAuction";
        Auction stateA = new Auction(
                auctionId, "testItem", new BigDecimal("100.00"),
                "testUserA", Instant.now().plusSeconds(3600), true, 1,
                null, null, 0, 0, 0
        );

        Auction stateB = new Auction(
                auctionId, "testItem",
                new BigDecimal("110.00"), "testUserB",
                Instant.now().plusSeconds(3600), true, 2,
                null, null, 0, 0, 0
        );
        AtomicInteger retryCount = new AtomicInteger(0);

        Mockito.when(auctionRepository.findById(auctionId))
                .thenReturn(Mono.defer(() -> {
                    if (retryCount.getAndIncrement() == 0) {
                        return Mono.just(stateA);
                    } else {
                        return Mono.just(stateB);
                    }
                }));
        Mockito.when(auctionRepository.updateWithVersion(any(Auction.class)))
                .thenReturn(Mono.just(false))
                .thenReturn(Mono.just(true));

        Mono<Auction> finalBidUserCMono = auctionService.placeBid(
                auctionId,
                "testUserC",
                new BigDecimal("115.00"),
                "127.0.0.1", "test-agent", 100
        );

        StepVerifier.create(finalBidUserCMono)
                .assertNext(stateC -> {
                    Assertions.assertEquals(new BigDecimal("115.00"), stateC.currentPrice());
                    Assertions.assertEquals("testUserC", stateC.highBidder());
                    Assertions.assertEquals(3, stateC.version());
                })
                .verifyComplete();
    }
}
