package com.walker.bidding.auction;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudListener {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final AuctionService auctionService;

    @PostConstruct
    public void listenForFraud() {
        log.info("🛡️ Bidding Engine listening for fraud alerts on 'auction:fraud'...");

        redisTemplate.listenTo(ChannelTopic.of("auction:fraud"))
                .map(message -> message.getMessage().split(":"))
                .filter(parts -> parts.length == 2)
                .flatMap(parts -> {
                    String auctionId = parts[0];
                    String username = parts[1];

                    log.warn("🚨 AI SENTINEL ALERT: Bot '{}' has been banned! Reverting price on {}...", username, auctionId);

                    return auctionService.revertBid(auctionId, username)
                            .doOnSuccess(_ -> log.info("✅ Revert successfully committed for {}...", auctionId))
                            .doOnError(err -> log.error("Failed to revert bid: ", err))
                            .onErrorResume(_ -> Mono.empty());
                })
                .subscribe();
    }}
