package com.walker.bidding.auction;

import com.walker.bidding.config.DatabaseInitializer;
import com.walker.bidding.exception.ConcurrentBidException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(AuctionController.class)
class AuctionControllerTest {

    @Autowired
    private WebTestClient webTestClient;
    @MockitoBean
    private AuctionService auctionService;
    @MockitoBean
    private DatabaseInitializer databaseInitializer;

    @Test
    void placeBid_whenValid_shouldReturn200Ok() {
        String auctionId = "test-1";
        BigDecimal bidAmount = new BigDecimal("150.00");
        Auction updatedAuction = new Auction(
                auctionId, "item-1", bidAmount, "webUser",
                Instant.now().plusSeconds(3600), true, 2,
                "127.0.0.1", "test-agent", 150, 1, 0
        );

        when(auctionService.placeBid(eq(auctionId), eq("webUser"), eq(bidAmount), any(), any(), eq(150)))
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
    void placeBid_whenCollisionOccurs_shouldReturn409Conflict() {
        String auctionId = "test-1";
        BigDecimal bidAmount = new BigDecimal("150.00");

        when(auctionService.placeBid(eq(auctionId), eq("unluckyUser"), eq(bidAmount), any(), any(), eq(120)))
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
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void placeBid_RejectsRequest_WhenDatabaseIsSeeding() {
        when(databaseInitializer.isSeeding()).thenReturn(true);

        BidRequest request = new BidRequest("user-1", new BigDecimal("15.00"), 120);

        webTestClient.post()
                .uri("/api/auctions/auc-1/bids")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                // 👇 FIX: ProblemDetail stores the message in "detail", not "message"
                .jsonPath("$.detail").isEqualTo("System is currently initializing. Please try again in a few seconds.");
    }
}
