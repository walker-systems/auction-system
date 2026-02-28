package com.walkersystems.sentinel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(RateLimitController.class)
public class RateLimiterControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    public void testAllowedRequest_Returns200() {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Mono.just(true));

        webTestClient.get()
                .uri("/check?capacity=10&rate=1")
                .header("X-User-ID", "test-user")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(true);

    }

    @Test
    public void testBlockedRequest_Returns429() {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Mono.just(false));

        webTestClient.get()
                .uri("/check?capacity=10&rate=1")
                .header("X-User-ID", "spammer")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(false);
    }

}
