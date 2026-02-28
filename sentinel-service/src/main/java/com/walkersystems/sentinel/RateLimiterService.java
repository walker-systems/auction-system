package com.walkersystems.sentinel;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    public RateLimiterService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }

    /**
     * Grant or deny user request (via token_bucket.lua) based on frequency of requests (represented as 'tokens').
     * @param identifier IP, API Key, or Username/Email; each unique ID is associated with one 'bucket' of tokens.
     * @param tokenCapacity Maximum number of tokens the bucket can hold (Burst limit).
     * @param tokenRefillRate Denotes the number of tokens automatically added to the bucket per second.
     * @param tokensRequested Cost of this specific request (usually 1 token).
     * @return Mono<Boolean> true if allowed, false if denied.
     */
    public Mono<Boolean> isAllowed(String identifier,
                                   int tokenCapacity,
                                   int tokenRefillRate,
                                   int tokensRequested) {
        
        // Check for unfulfillable request
        if (tokensRequested > tokenCapacity) {
            System.err.println("Request denied: cost (" + tokensRequested +
                               ") exceeds max token capacity (" + tokenCapacity + ")");
            return Mono.just(false);
        }
        
        var key = "rate_limit:" + identifier;
        var keys = List.of(key);                                     // KEYS[1]

        var rateArg = String.valueOf(tokenRefillRate);               // ARGV[1]
        var capacityArg = String.valueOf(tokenCapacity);             // ARGV[2]
        var nowArg = String.valueOf(Instant.now().getEpochSecond()); // ARGV[3]
        var requestedArg = String.valueOf(tokensRequested);          // ARGV[4]

        var args = List.of(rateArg, capacityArg, nowArg, requestedArg);


        return redisTemplate.execute(tokenBucketScript, keys, args)
                .next()
                .map(result -> {
                    Long allowed = (Long) result.getFirst(); // Redis returns a Long
                    return allowed == 1L;
                });
    }
}
