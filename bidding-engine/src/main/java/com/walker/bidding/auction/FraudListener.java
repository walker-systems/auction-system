package com.walker.bidding.auction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudListener {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ReactiveRedisConnectionFactory connectionFactory;
    private final AuctionService auctionService;

    @PostConstruct
    public void init() {
        log.info("🛡️ Bidding Engine listening for reliable fraud alerts on 'stream:auction:fraud'...");

        redisTemplate.opsForStream()
                .createGroup("stream:auction:fraud", ReadOffset.from("0"), "engine-group")
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, String>> options =
                StreamReceiver.StreamReceiverOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        StreamReceiver<String, MapRecord<String, String, String>> receiver =
                StreamReceiver.create(connectionFactory, options);

        receiver.receiveAutoAck(
                        Consumer.from("engine-group", "engine-instance-1"),
                        StreamOffset.create("stream:auction:fraud", ReadOffset.lastConsumed())
                )
                .flatMap(record -> {
                    String payload = record.getValue().get("payload");
                    if (payload != null) {
                        String[] parts = payload.split(":");
                        if (parts.length == 2) {
                            String auctionId = parts[0];
                            String fraudUser = parts[1];
                            log.warn("🚨 Fraud Alert received for User: {}. Reverting bid on Auction: {}", fraudUser, auctionId);
                            return auctionService.revertBid(auctionId, fraudUser);
                        }
                    }
                    return Mono.empty();
                })
                .subscribe();
    }
}
