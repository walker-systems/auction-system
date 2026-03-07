package com.walkersystems.sentinel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class RateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final RedisScript<List<?>> tokenBucketScript;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public RateLimiterService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = (RedisScript<List<?>>) (RedisScript) RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }

    public Mono<Boolean> isAllowed(String identifier, int tokenCapacity, int tokenRefillRate, int tokensRequested) {

        if (tokensRequested > tokenCapacity) {
            log.warn("⚠️ Request denied: cost ({}) exceeds max token capacity ({})",
                    tokensRequested, tokenCapacity);
            return Mono.just(false);
        }

        String key = "rate_limit:" + identifier;
        List<String> keys = List.of(key);                                     // KEYS[1]
        String rateArg = String.valueOf(tokenRefillRate);                     // ARGV[1]
        String capacityArg = String.valueOf(tokenCapacity);                   // ARGV[2]
        String nowArg = String.valueOf(Instant.now().getEpochSecond());       // ARGV[3]
        String requestedArg = String.valueOf(tokensRequested);                // ARGV[4]

        List<String> args = List.of(rateArg, capacityArg, nowArg, requestedArg);

        return redisTemplate.execute(tokenBucketScript, keys, args)
                .next()
                .map(result -> {
                    Long allowed = (Long) result.getFirst();
                    return allowed == 1L;
                });
    }
}
