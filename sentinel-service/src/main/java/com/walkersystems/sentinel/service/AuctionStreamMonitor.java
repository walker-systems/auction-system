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

                // TODO: In the next ticket, extract this real telemetry from the bid.
                // For now, we simulate a highly suspicious bot bid to test the AI.
                FraudCheckRequest request = new FraudCheckRequest(
                        "192.168.1.100",
                        "java-webclient-test",
                        15,     // 15ms reaction time (superhuman)
                        45,     // 45 bids in the last minute (bot behavior)
                        1,      // New IP address
                        500.0   // Bid amount
                );

                // Call the Python FastAPI microservice
                return fastApiWebClient.post()
                        .uri("/predict")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(FraudCheckResponse.class)
                        .flatMap(response -> {
                            if (response.isFraud()) {
                                log.warn("🚨 AI SENTINEL ALERT: Fraudulent activity detected from user '{}'! Probability: {}%",
                                        auction.highBidder(), (response.fraudProbability() * 100));

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
    }
}
