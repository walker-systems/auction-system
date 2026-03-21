package com.walkersystems.sentinel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkersystems.sentinel.model.AuctionDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionStreamMonitorTest {

    @Mock
    private ReactiveRedisTemplate<String, String> mockRedisTemplate;

    @Mock
    private ReactiveSetOperations<String, String> mockSetOperations;

    @Mock
    private ReactiveStreamOperations<String, Object, Object> mockStreamOperations;

    @Mock
    private ExchangeFunction mockExchangeFunction;

    private AuctionStreamMonitor UUT_AuctionStreamMonitor;

    @BeforeEach
    void setUp() {
        WebClient mockWebClient = WebClient.builder()
                .exchangeFunction(mockExchangeFunction)
                .build();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        UUT_AuctionStreamMonitor = new AuctionStreamMonitor(
                mockRedisTemplate,
                null,
                new ObjectMapper(),
                mockWebClient,
                registry
        );

        ReflectionTestUtils.setField(
                UUT_AuctionStreamMonitor,
                "fraudChecksTotal",
                registry.counter("test.fraud.checks")
        );
    }

    @Test
    void testFraudulentBid_isBanned_AndRollbackTriggered() {
        AuctionDto mockAuction = new AuctionDto(
                "auc-1", "item-123", "bot_net_alpha", true,
                "192.168.1.1", "BotNet/1.0", 10, 60, 1, new BigDecimal("500.00")
        );

        String simAIResponseJson = "[{\"id\": \"auc-1|bot_net_alpha\", \"fraud_probability\": 0.99, \"is_fraud\": true}]";

        ClientResponse simClientResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(simAIResponseJson)
                .build();
        when(mockExchangeFunction.exchange(any())).thenReturn(Mono.just(simClientResponse));

        when(mockRedisTemplate.opsForSet()).thenReturn(mockSetOperations);
        when(mockSetOperations.add("banned_users", "bot_net_alpha")).thenReturn(Mono.just(1L));

        when(mockRedisTemplate.opsForStream()).thenReturn(mockStreamOperations);
        when(mockStreamOperations.add(anyString(), any(Map.class)))
                .thenReturn(Mono.just(RecordId.of("12345-0")));

        StepVerifier.create(UUT_AuctionStreamMonitor.analyzeBatchWithAI(List.of(mockAuction)))
                .verifyComplete();

        verify(mockStreamOperations).add("stream:auction:fraud", Map.of("payload", "auc-1:bot_net_alpha"));
    }
}
