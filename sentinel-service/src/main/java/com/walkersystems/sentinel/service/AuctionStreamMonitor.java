package com.walkersystems.sentinel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkersystems.sentinel.model.AuctionDto;
import com.walkersystems.sentinel.model.FraudCheckRequest;
import com.walkersystems.sentinel.model.FraudCheckResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionStreamMonitor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient fastApiWebClient;
    private final MeterRegistry meterRegistry;

    private Counter fraudChecksTotal;

    @PostConstruct
    public void startMonitoring() {
        log.info("🛡️ Sentinel Stream Monitor starting... listening to 'auction:updates:*' with 50-bid batching");

        this.fraudChecksTotal = Counter.builder("fraud.checks.total")
                .description("Total number of bids evaluated by the ML model")
                .register(meterRegistry);

        redisTemplate.listenTo(PatternTopic.of("auction:updates:*"))
                .mapNotNull(message -> {
                    try {
                        return objectMapper.readValue(message.getMessage(), AuctionDto.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(auction -> auction.highBidder() != null && !"System".equals(auction.highBidder()))
                .bufferTimeout(50, Duration.ofMillis(100))
                .flatMap(this::analyzeBatchWithAI)
                .subscribe();
    }

    Mono<Void> analyzeBatchWithAI(List<AuctionDto> batch) {
        if (batch.isEmpty()) {
            return Mono.empty();
        }

        List<FraudCheckRequest> requests = batch.stream()
                .map(auction -> new FraudCheckRequest(
                        auction.id() + "|" + auction.highBidder(),
                        auction.ipAddress() != null ? auction.ipAddress() : "unknown",
                        auction.userAgent() != null ? auction.userAgent() : "unknown",
                        auction.reactionTimeMs(),
                        auction.bidCountLastMin(),
                        auction.isNewIp(),
                        auction.currentPrice()
                ))
                .collect(Collectors.toList());

        return fastApiWebClient.post()
                .uri("/predict")
                .bodyValue(requests)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FraudCheckResponse>>() {})
                .doOnNext(responses -> fraudChecksTotal.increment(responses.size())) // for Grafana viz
                .flatMapMany(Flux::fromIterable)
                .filter(FraudCheckResponse::isFraud)
                .flatMap(response -> {
                    String[] parts = response.id().split("\\|");
                    if (parts.length != 2) return Mono.empty();
                    String auctionId = parts[0];
                    String bidder = parts[1];

                    String prob = String.format("%.2f", response.fraudProbability() * 100);
                    log.warn("🚨 AI SENTINEL ALERT: Fraudulent activity detected from user '{}'! Probability: {}%",
                            bidder, prob);

                    return redisTemplate.opsForSet().add("banned_users", bidder)
                            .then(Mono.defer(() -> {
                                String payload = auctionId + ":" + bidder;
                                return redisTemplate.convertAndSend("auction:fraud", payload).then();
                            }));
                })
                .then()
                .onErrorResume(e -> {
                    log.error("Failed to process AI hook batch: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
