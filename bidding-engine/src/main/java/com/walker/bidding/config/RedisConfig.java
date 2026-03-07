package com.walker.bidding.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walker.bidding.auction.Auction;
import org.jspecify.annotations.NullMarked; // <-- Imported NullMarked
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;

@Configuration
@NullMarked
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Auction> auctionRedisTemplate(ReactiveRedisConnectionFactory factory) {

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        RedisSerializer<Auction> valueSerializer = new RedisSerializer<Auction>() {

            @Override
            public byte[] serialize(@Nullable Auction auction) throws SerializationException {
                if (auction == null) return new byte[0];
                try {
                    return objectMapper.writeValueAsBytes(auction);
                } catch (JsonProcessingException e) {
                    throw new SerializationException("Error serializing Auction", e);
                }
            }

            @Override
            public @Nullable Auction deserialize(byte @Nullable [] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return objectMapper.readValue(bytes, Auction.class);
                } catch (IOException e) {
                    throw new SerializationException("Error deserializing Auction", e);
                }
            }
        };

        RedisSerializationContext<String, Auction> context = RedisSerializationContext
                .<String, Auction>newSerializationContext(keySerializer)
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
