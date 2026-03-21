package com.walkersystems.sentinel;

import com.walkersystems.sentinel.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class RateLimiterTest {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int CAPACITY = 10;
    private static final int REFILL_RATE = 10;
    private static final int REQUEST_COST = 1;

    @BeforeEach
    public void setup() {
        redisTemplate.execute(conn -> conn.serverCommands().flushAll())
                .blockLast();

    }

    @Test
    public void testAllowedRequest() {

        StepVerifier.create(rateLimiterService.isAllowed("test_user", CAPACITY, REFILL_RATE, REQUEST_COST))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    public void testBlockedRequest() {

        int noRefill = 0; // Set to 0 to prevent a refill if request happens to run slowly

        for (int i = 0; i < 10; i++) {
            rateLimiterService.isAllowed("greedy_user", CAPACITY, noRefill, REQUEST_COST).block();
        }

        // 11th request must fail
        StepVerifier.create(rateLimiterService.isAllowed("greedy_user", CAPACITY, noRefill, REQUEST_COST))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    public void testCapacityExceeded() {

        // Request 11 tokens when capacity is 10
        int oversizedRequest = 11;

        StepVerifier.create(rateLimiterService.isAllowed("oversize_user", CAPACITY, REFILL_RATE, oversizedRequest))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    public void testThunderingHerd_ExactlyCapacityAllowed() {
        String testUser = "thundering_herd_user";
        int capacity = 10;
        int refillRate = 1;

        // Fire 50 requests concurrently
        Long allowedCount = reactor.core.publisher.Flux.range(1, 50)
                .flatMap(i -> rateLimiterService.isAllowed(testUser, capacity, refillRate, 1))
                .filter(allowed -> allowed) // Keep only the requests that returned true
                .count()
                .block();

        // No matter the concurrency, the Lua script MUST restrict success to exactly the bucket capacity
        assert allowedCount != null;
        org.junit.jupiter.api.Assertions.assertEquals(
                capacity,
                allowedCount.intValue(),
                "Exactly 10 requests should be allowed out of 50 concurrent ones"
        );
    }
}
