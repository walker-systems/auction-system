package com.walkersystems.sentinel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkersystems.sentinel.model.AuctionDto;
import com.walkersystems.sentinel.model.FraudCheckRequest;
import com.walkersystems.sentinel.model.FraudCheckResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionStreamMonitor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient fastApiWebClient;

    @PostConstruct
    public void startMonitoring() {
        log.info("🛡️ Sentinel Stream Monitor starting... listening to 'auction:updates'");

        redisTemplate.listenTo(ChannelTopic.of("auction:updates"))
                .flatMap(message -> analyzeBidWithAI(message.getMessage()))
                .subscribe();
    }

    private Mono<Void> analyzeBidWithAI(String rawJson) {
        return Mono.defer(() -> {
            try {
                AuctionDto auction = objectMapper.readValue(rawJson, AuctionDto.class);
                log.info("🧠 Sentinel analyzing bid by {}...", auction.highBidder());

                FraudCheckRequest request = new FraudCheckRequest(
                        auction.ipAddress(),
                        auction.userAgent(),
                        auction.reactionTimeMs(),
                        auction.bidCountLastMin(),
                        auction.isNewIp(),
                        auction.bidAmount()
                );

                return fastApiWebClient.post()
                        .uri("/predict")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(FraudCheckResponse.class)
                        .flatMap(response -> {
                            if (response.isFraud()) {
                                log.warn("🚨 AI SENTINEL ALERT: Fraudulent activity detected from user '{}'! Probability: String.format(\"%.2f%%\", response.fraudProbability() * 100)",
                                        auction.highBidder());

                                String payload = auction.id() + ":" + auction.highBidder();
                                return redisTemplate.convertAndSend("auction:fraud", payload).then();
                            } else {
                                log.info("✅ AI Sentinel cleared user '{}'.", auction.highBidder());
                                return Mono.empty();
                            }
                        });

            } catch (Exception e) {
                return Mono.error(e);
            }
        }).onErrorResume(e -> {
            log.error("💥 Failed to process AI hook: {}", e.getMessage());
            return Mono.empty();
        });
    }}
