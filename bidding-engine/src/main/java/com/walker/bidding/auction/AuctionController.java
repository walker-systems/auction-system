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

import java.util.Optional;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Slf4j
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping("/{id}/bids")
    public Mono<Auction> placeBid(@PathVariable String id,
                                  @Valid @RequestBody BidRequest request,
                                  ServerHttpRequest httpRequest) {

        String ipAddress = Optional.ofNullable(httpRequest.getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        log.info("📡 Received bid on {} for ${} | IP: {} | Agent: {}",
                id, request.amount(), ipAddress, userAgent);

        return auctionService.placeBid(id, request.bidder(), request.amount(), ipAddress, userAgent, request.reactionTimeMs());
    }

    // Existing individual stream (kept for legacy/testing purposes)
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Auction> streamAuctionUpdates(@PathVariable String id) {
        return auctionService.streamAuctionUpdates(id);
    }

    // 👇 THE FIX: The new Global Multiplexed Stream Endpoint!
    @GetMapping(value = "/stream/all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Auction> streamAllAuctions() {
        log.info("🌐 Client connected to Global Multiplexed Stream");
        return auctionService.streamAllAuctionUpdates();
    }

    @GetMapping
    public Flux<Auction> getAllAuctions() {
        log.info("Fetching all active auctions for storefront");
        return auctionService.getAllAuctions();
    }

    @PostMapping("/{id}/max-bids")
    public Mono<Auction> placeMaxBid(
            @PathVariable String id,
            @RequestBody MaxBidRequest request) {

        return auctionService.placeMaxBid(
                id,
                request.bidderId(),
                request.maxBid(),
                request.telemetry().ipAddress(),
                request.telemetry().userAgent(),
                request.telemetry().reactionTimeMs()
        );
    }
}
