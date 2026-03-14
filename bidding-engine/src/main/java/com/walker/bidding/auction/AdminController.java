package com.walker.bidding.auction;

import com.walker.bidding.config.DatabaseInitializer;
import com.walker.bidding.config.DemoBotService;
import com.walker.bidding.config.LogStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DemoBotService demoBotService;
    private final DatabaseInitializer databaseInitializer;
    private final LogStreamService logStreamService;
    private final AuctionService auctionService;

    @GetMapping("/seeding-status")
    public Mono<Boolean> checkSeedingStatus() {
        return Mono.just(databaseInitializer.isSeeding());
    }

    @PostMapping("/start-bots")
    public Mono<Void> startBots() {
        demoBotService.startBotSwarm();
        return Mono.empty();
    }

    @PostMapping("/stop-bots")
    public Mono<Void> stopBots() {
        demoBotService.stopBotSwarm();
        return Mono.empty();
    }

    @PostMapping("/reset")
    public Mono<Void> resetSystem() {
        demoBotService.stopBotSwarm();
        databaseInitializer.resetAndSeedDatabase().subscribe();
        return Mono.empty();
    }

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs() {
        Flux<ServerSentEvent<String>> logs = logStreamService.getLogStream()
                .map(log -> ServerSentEvent.<String>builder().data(log).build());

        Flux<ServerSentEvent<String>> keepAlive = Flux.interval(Duration.ofMillis(500))
                .map(tick -> ServerSentEvent.<String>builder().data("HEARTBEAT").build());

        return Flux.merge(logs, keepAlive);
    }

    @GetMapping("/telemetry")
    public Mono<java.util.Map<String, Object>> getTelemetry() {
        return Mono.just(java.util.Map.of(
                "p99LatencyMs", auctionService.getP99LatencyMs()
        ));
    }
}
