package com.walkersystems.sentinel.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionStreamMonitorTest {

    @Mock
    private ReactiveRedisTemplate<String, String> mockRedisTemplate;

    @Mock
    private ReactiveSetOperations<String, String> mockSetOperations;

    @Mock
    private ExchangeFunction mockExchangeFunction;

    private AuctionStreamMonitor UUT_AuctionStreamMonitor; // Unit Under Test (UUT)

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);

        WebClient webClient = WebClient.builder().exchangeFunction(mockExchangeFunction).build();

        UUT_AuctionStreamMonitor = new AuctionStreamMonitor(mockRedisTemplate, objectMapper, webClient);
    }

    @Test
    void testSystemBid_isIgnored_NoApiCallsMade() {
        String simSystemBidJson = "{\"id\":\"auc-1\",\"highBidder\":\"System\",\"currentPrice\":10.00}";

        StepVerifier.create(UUT_AuctionStreamMonitor.analyzeBidWithAI(simSystemBidJson))
                .verifyComplete();

        verifyNoInteractions(mockExchangeFunction);
        verifyNoInteractions(mockRedisTemplate);
    }

    @Test
    void testFraudulentBid_isBanned_AndRollbackTriggered() {
        String simFraudUserJson = """
            {
              "id": "auc-1",
              "itemId": "item-123",
              "currentPrice": 500.00,
              "highBidder": "bot_net_alpha",
              "ipAddress": "192.168.1.1",
              "userAgent": "BotNet/1.0",
              "reactionTimeMs": 10,
              "bidCountLastMin": 60,
              "isNewIp": 1
            }
            """;

        String simAIResponseJson = "{\"fraud_probability\": 0.99, \"is_fraud\": true}";
        ClientResponse simClientResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(simAIResponseJson)
                .build();
        when(mockExchangeFunction.exchange(any())).thenReturn(Mono.just(simClientResponse));

        when(mockRedisTemplate.opsForSet()).thenReturn(mockSetOperations);
        when(mockSetOperations.add("banned_users", "bot_net_alpha")).thenReturn(Mono.just(1L));
        when(mockRedisTemplate.convertAndSend("auction:fraud", "auc-1:bot_net_alpha")).thenReturn(Mono.just(1L));

        StepVerifier.create(UUT_AuctionStreamMonitor.analyzeBidWithAI(simFraudUserJson))
                .verifyComplete();

        verify(mockSetOperations).add("banned_users", "bot_net_alpha");

        verify(mockRedisTemplate).convertAndSend("auction:fraud", "auc-1:bot_net_alpha");
    }
}
