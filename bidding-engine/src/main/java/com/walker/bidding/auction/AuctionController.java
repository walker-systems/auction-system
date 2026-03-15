package com.walker.bidding.auction;

import com.walker.bidding.config.DatabaseInitializer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Slf4j
public class AuctionController {

    private final AuctionService auctionService;
    private final DatabaseInitializer databaseInitializer;

    @PostMapping("/{id}/bids")
    public Mono<Auction> placeBid(@PathVariable String id,
                                  @Valid @RequestBody BidRequest request,
                                  ServerHttpRequest httpRequest) {
        if (databaseInitializer.isSeeding()) {
            return Mono.error(new IllegalStateException("System is currently initializing. Please try again in a few seconds."));
        }

        String ipAddress = Optional.ofNullable(httpRequest.getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        log.info("📡 Received bid on {} for ${} | IP: {} | Agent: {}",
                id, request.amount(), ipAddress, userAgent);

        return auctionService.placeBid(id, request.bidder(), request.amount(), ipAddress, userAgent, request.reactionTimeMs());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Auction> streamAuctionUpdates(@PathVariable String id) {
        return auctionService.streamAuctionUpdates(id);
    }

    @GetMapping(value = "/stream/all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Auction> streamAllAuctions() {
        return auctionService.streamAllAuctionUpdates();
    }

    @GetMapping
    public Flux<Auction> getAllAuctions() {
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
