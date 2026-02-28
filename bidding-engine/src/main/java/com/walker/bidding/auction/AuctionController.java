package com.walker.bidding.auction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
                                  @Valid @RequestBody BidRequest request) {
        log.info("Received HTTP POST to place bid on auction {} for ${}", id, request.amount());
        return auctionService.placeBid(id, request.bidder(), request.amount());
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
