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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DemoBotService demoBotService;
    private final DatabaseInitializer databaseInitializer;
    private final LogStreamService logStreamService;

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
        return databaseInitializer.resetAndSeedDatabase();
    }

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs() {
        return logStreamService.getLogStream()
                .map(log -> ServerSentEvent.builder(log).build());
    }
}
