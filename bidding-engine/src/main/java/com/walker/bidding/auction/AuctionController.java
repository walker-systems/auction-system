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

        // TODO: Update to check X-Forwarded-For to find real user IP instead of .getRemoteAddress()
        // TODO: Train model to recognize "unknown" ipAddress as a suspicious feature
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

    @GetMapping
    public Flux<Auction> getAllAuctions() {
        log.info("Fetching all active auctions for storefront");
        return auctionService.getAllAuctions();
    }
}
