package com.walker.bidding.config;

import com.walker.bidding.auction.Auction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Auction> auctionRedisTemplate(ReactiveRedisConnectionFactory factory) {

        StringRedisSerializer keySerializer 
                = new StringRedisSerializer();

        RedisSerializer<Auction> valueSerializer
                = (RedisSerializer<Auction>) (RedisSerializer<?>) RedisSerializer.json(); // double cast avoids compiler error

        RedisSerializationContext<String, Auction> context
                = RedisSerializationContext
                    .<String, Auction>newSerializationContext(keySerializer)
                    .value(valueSerializer)
                    .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
