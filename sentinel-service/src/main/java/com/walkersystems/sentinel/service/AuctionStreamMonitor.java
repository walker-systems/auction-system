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
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionStreamMonitor {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ReactiveRedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final WebClient fastApiWebClient;
    private final MeterRegistry meterRegistry;

    private Counter fraudChecksTotal;

    @PostConstruct
    public void startMonitoring() {
        log.info("🛡️ Sentinel Stream Monitor starting... consuming from Stream 'stream:auction:updates' with Consumer Groups");

        this.fraudChecksTotal = Counter.builder("fraud.checks.total")
                .description("Total number of bids evaluated by the ML model")
                .register(meterRegistry);

        redisTemplate.opsForStream()
                .createGroup("stream:auction:updates", ReadOffset.from("0"), "sentinel-group")
                .onErrorResume(_ -> Mono.empty())
                .subscribe();

        StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, String>> options =
                StreamReceiver.StreamReceiverOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        StreamReceiver<String, MapRecord<String, String, String>> receiver =
                StreamReceiver.create(connectionFactory, options);

        receiver.receiveAutoAck(
                        Consumer.from("sentinel-group", "sentinel-instance-1"),
                        StreamOffset.create("stream:auction:updates", ReadOffset.lastConsumed())
                )
                .mapNotNull(record -> {
                    try {
                        String json = record.getValue().get("auction");
                        return objectMapper.readValue(json, AuctionDto.class);
                    } catch (Exception e) {
                        log.error("Failed to parse auction from stream", e);
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
                .doOnNext(responses -> fraudChecksTotal.increment(responses.size()))
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
                                return redisTemplate.opsForStream().add(
                                        "stream:auction:fraud",
                                        Map.of("payload", payload)
                                ).then();
                            }));
                })
                .then()
                .onErrorResume(e -> {
                    log.error("Failed to process AI hook batch: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
