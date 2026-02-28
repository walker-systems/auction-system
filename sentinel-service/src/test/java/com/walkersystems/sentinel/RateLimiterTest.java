package com.walkersystems.sentinel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

@SpringBootTest
public class RateLimiterTest {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int CAPACITY = 10;
    private static final int REFILL_RATE = 10;
    private static final int REQUEST_COST = 10;

    @BeforeEach
    public void setup() {
        redisTemplate.execute(conn -> conn.serverCommands().flushAll())
                .blockLast(); // Make sure data is cleared before running next test

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
}
