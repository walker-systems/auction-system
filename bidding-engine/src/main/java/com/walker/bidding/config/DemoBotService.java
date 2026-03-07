package com.walker.bidding.config;

import com.walker.bidding.auction.Auction;
import com.walker.bidding.auction.AuctionRepository;
import com.walker.bidding.auction.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DemoBotService {

    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private Disposable botTask;

    private final List<String> AI_PERSONAS = List.of(
            "bot_user_99", "legit_human_dave", "sniper_script_x", "susan_the_buyer",
            "auto_bidder_v2", "casual_bidder_bob", "deal_hunter_99", "script_kiddie_1",
            "human_alice", "bot_net_alpha", "bargain_shopper"
    );

    public synchronized void startBotSwarm() {
        if (botTask != null && !botTask.isDisposed()) {
            log.info("Bots are already running!");
            return;
        }
        log.info("🤖 Demo Bot Swarm ACTIVATED!");

        botTask = Flux.interval(Duration.ofMillis(200), Duration.ofMillis(800))
                .flatMap(tick -> placeRandomBid())
                .subscribe();
    }

    public synchronized void stopBotSwarm() {
        if (botTask != null && !botTask.isDisposed()) {
            botTask.dispose();
            log.info("🛑 Demo Bot Swarm DEACTIVATED.");
        }
    }

    private Mono<Void> placeRandomBid() {
        return auctionRepository.findAll()
                .filter(Auction::active)
                .collectList()
                .flatMap(activeAuctions -> {
                    if (activeAuctions.isEmpty()) return Mono.empty();

                    Auction target = activeAuctions.get(ThreadLocalRandom.current().nextInt(activeAuctions.size()));
                    String botName = AI_PERSONAS.get(ThreadLocalRandom.current().nextInt(AI_PERSONAS.size()));

                    int randomIncrement = ThreadLocalRandom.current().nextInt(1, 101);
                    BigDecimal bidAmount = target.currentPrice().add(BigDecimal.valueOf(randomIncrement));

                    boolean isSuspicious = botName.toLowerCase().contains("bot") || botName.toLowerCase().contains("script");

                    String ipAddress = isSuspicious ? "192.168.1.99" : "72.14.213.15";
                    String userAgent = isSuspicious ? "python-requests/2.31.0" : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0";

                    int reactionTimeMs = isSuspicious ?
                            ThreadLocalRandom.current().nextInt(10, 51) :
                            ThreadLocalRandom.current().nextInt(400, 2001);

                    log.info("🤖 Bot '{}' bidding ${} on {} (Reaction: {}ms)",
                            botName, bidAmount, target.itemId(), reactionTimeMs);

                    return auctionService.placeBid(
                            target.id(),
                            botName,
                            bidAmount,
                            ipAddress,
                            userAgent,
                            reactionTimeMs
                    ).onErrorResume(e -> Mono.empty());
                }).then();
    }
}
