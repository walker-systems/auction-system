package com.walker.bidding.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walker.bidding.auction.Auction;
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
public class RedisConfig {

    @Bean
    public RedisSerializer<Auction> auctionSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Auction auction) throws SerializationException {
                if (auction == null) return new byte[0];
                try {
                    return objectMapper.writeValueAsBytes(auction);
                } catch (JsonProcessingException e) {
                    throw new SerializationException("Error serializing Auction", e);
                }
            }

            @Override
            public Auction deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return objectMapper.readValue(bytes, Auction.class);
                } catch (IOException e) {
                    throw new SerializationException("Error deserializing Auction", e);
                }
            }
        };
    }

    @Bean
    public ReactiveRedisTemplate<String, Auction> auctionRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            RedisSerializer<Auction> auctionSerializer) {

        RedisSerializationContext<String, Auction> context = RedisSerializationContext
                .<String, Auction>newSerializationContext(new StringRedisSerializer())
                .value(auctionSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
