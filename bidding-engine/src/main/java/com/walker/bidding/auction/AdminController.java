package com.walker.bidding.auction;

import com.walker.bidding.config.DatabaseInitializer;
import com.walker.bidding.config.DemoBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DemoBotService botService;
    private final DatabaseInitializer dbInitializer;

    @PostMapping("/start-bots")
    public Mono<Void> startBots() {
        botService.startBotSwarm();
        return Mono.empty();
    }

    @PostMapping("/stop-bots")
    public Mono<Void> stopBots() {
        botService.stopBotSwarm();
        return Mono.empty();
    }

    @PostMapping("/reset")
    public Mono<Void> resetSystem() {
        botService.stopBotSwarm();
        return dbInitializer.resetAndSeedDatabase();
    }
}
