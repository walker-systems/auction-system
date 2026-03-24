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

    private final RedisScript<List<?>> tokenBucketLua;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public RateLimiterService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketLua = (RedisScript<List<?>>) (RedisScript) RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }

    public Mono<Boolean> isAllowed(String identifier, int tokenCapacity, int tokenRefillRate, int tokensRequested) {

        if (tokensRequested > tokenCapacity) {
            log.warn("⚠️ Request denied: cost ({}) exceeds max token capacity ({})",
                    tokensRequested, tokenCapacity);
            return Mono.just(false);
        }

        String key = "rate_limit:" + identifier;
        String rateArg = String.valueOf(tokenRefillRate);
        String capacityArg = String.valueOf(tokenCapacity);
        String requestedArg = String.valueOf(tokensRequested);

        List<String> keys = List.of(key);
        List<String> args = List.of(rateArg, capacityArg, requestedArg);

        return redisTemplate.execute(tokenBucketLua, keys, args)
                .next()
                .map(result -> {
                    Long allowed = (Long) result.getFirst();
                    boolean isApproved = (allowed != null && allowed == 1L);

                    if (!isApproved) {
                        log.warn("⚠️ Request denied: Rate limit exceeded for user [{}]", identifier);
                    }

                    return isApproved;
                });
    }}
