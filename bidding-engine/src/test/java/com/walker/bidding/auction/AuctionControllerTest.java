package com.walker.bidding.auction;

import com.walker.bidding.exception.ConcurrentBidException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@WebFluxTest(AuctionController.class)
class AuctionControllerTest {

    @Autowired
    private WebTestClient webTestClient;
    @MockitoBean
    private AuctionService auctionService;

    @Test
    void placeBid_whenValid_shouldReturn200Ok() {
        String auctionId = "test-1";
        BigDecimal bidAmount = new BigDecimal("150.00");
        Auction updatedAuction = new Auction(
                auctionId,
                "item-1",
                bidAmount,
                "webUser",
                Instant.now().plusSeconds(3600),
                true,
                2,
                "127.0.0.1", "test-agent", 150, 1, 0
        );

        Mockito.when(auctionService.placeBid(eq(auctionId), eq("webUser"), eq(bidAmount), any(), any(), eq(150)))
                .thenReturn(Mono.just(updatedAuction));

        webTestClient.post()
                .uri("/api/auctions/{id}/bids", auctionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "bidder": "webUser",
                          "amount": 150.00,
                          "reactionTimeMs": 150
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentPrice").isEqualTo(150.00)
                .jsonPath("$.highBidder").isEqualTo("webUser");
    }

    @Test
    void placeBid_whenCollisionOccurs_shouldReturn400BadRequest() {
        String auctionId = "test-1";
        BigDecimal bidAmount = new BigDecimal("150.00");

        Mockito.when(auctionService.placeBid(eq(auctionId), eq("unluckyUser"), eq(bidAmount), any(), any(), eq(120)))
                .thenReturn(Mono.error(new ConcurrentBidException("Bid collision")));

        webTestClient.post()
                .uri("/api/auctions/{id}/bids", auctionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "bidder": "unluckyUser",
                          "amount": 150.00,
                          "reactionTimeMs": 120
                        }
                        """)
                .exchange()
                // 👇 THE FIX: We now gracefully handle these errors with a 400 response for the UI!
                .expectStatus().isBadRequest();
    }
}
