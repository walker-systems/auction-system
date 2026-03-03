package com.walker.bidding.auction;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

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
                .subscribe(message -> {
                    String[] parts = message.getMessage().split(":");
                    if (parts.length == 2) {
                        auctionService.revertFraudulentBid(parts[0], parts[1])
                                .doOnSuccess(v -> log.info("✅ Revert successfully committed to DB and published."))
                                .doOnError(err -> log.error("Failed to revert bid: ", err))
                                .subscribe(); // Empty subscribe kicks off the reactive chain safely
                    }
                });
    }
}
