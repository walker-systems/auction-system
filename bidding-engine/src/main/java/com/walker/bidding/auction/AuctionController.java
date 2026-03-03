package com.walker.bidding.auction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Slf4j
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping("/{id}/bids")
    public Mono<Auction> placeBid(@PathVariable String id,
                                  @Valid @RequestBody BidRequest request,
                                  ServerHttpRequest httpRequest) { // <-- 1. Inject the request context

        // 2. Extract the Telemetry Data
        String ipAddress = httpRequest.getRemoteAddress() != null ?
                httpRequest.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        log.info("📡 Received bid on {} for ${} | IP: {} | Agent: {}",
                id, request.amount(), ipAddress, userAgent);

        // 3. Pass the new data down to the Service Layer
        // (Note: This will show a red error in your IDE until we update the AuctionService in the next step!)
        return auctionService.placeBid(id, request.bidder(), request.amount(), ipAddress, userAgent, request.reactionTimeMs());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Auction> streamLiveBids(@PathVariable String id) {
        log.info("Client connected to live stream for auction {}", id);
        return auctionService.streamAuctionUpdates(id);
    }

    @GetMapping
    public Flux<Auction> getAllAuctions() {
        log.info("Fetching all active auctions for storefront");
        return auctionService.getAllAuctions();
    }
}
