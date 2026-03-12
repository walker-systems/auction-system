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
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DemoBotService {

    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private Disposable botTask;
    private Disposable cacheTask;

    private List<Auction> hotTargets = new CopyOnWriteArrayList<>();

    private final List<String> AI_PERSONAS = List.of(
            "bot_user_99", "legit_human_dave", "sniper_script_x", "susan_the_buyer",
            "auto_bidder_v2", "casual_bidder_bob", "deal_hunter_99", "script_kiddie_1",
            "human_alice", "bot_net_alpha", "bargain_shopper"
    );

    public synchronized void startBotSwarm() {
        if (botTask != null && !botTask.isDisposed()) return;

        log.info("🤖 Demo Bot Swarm ACTIVATED! Commencing Bidding...");

        cacheTask = Flux.interval(Duration.ZERO, Duration.ofSeconds(5))
                .flatMap(tick -> auctionRepository.findAll()
                        .filter(Auction::active)
                        .collectList()
                        .map(list -> list.stream()
                                .sorted(Comparator.comparing(Auction::endsAt))
                                .limit(100)
                                .collect(Collectors.toList()))
                )
                .doOnNext(topItems -> this.hotTargets = topItems)
                .subscribe();

        botTask = Flux.interval(Duration.ofMillis(100))
                .flatMap(tick -> placeRandomBid())
                .subscribe();
    }

    public synchronized void stopBotSwarm() {
        if (botTask != null && !botTask.isDisposed()) botTask.dispose();
        if (cacheTask != null && !cacheTask.isDisposed()) cacheTask.dispose();
        log.info("🛑 Demo Bot Swarm DEACTIVATED.");
    }

    private Mono<Void> placeRandomBid() {
        String targetId = "auc-" + ThreadLocalRandom.current().nextInt(1, 10001);

        return auctionRepository.findById(targetId)
                .flatMap(target -> {
                    if (!target.active()) return Mono.empty();

                    String botName = AI_PERSONAS.get(ThreadLocalRandom.current().nextInt(AI_PERSONAS.size()));
                    double randomIncrement = 1.0 + (100.0 * ThreadLocalRandom.current().nextDouble());
                    BigDecimal bidAmount = target.currentPrice()
                            .add(BigDecimal.valueOf(randomIncrement))
                            .setScale(2, RoundingMode.HALF_UP);

                    boolean isSuspicious = botName.toLowerCase().contains("bot") || botName.toLowerCase().contains("script");
                    String ipAddress = isSuspicious ? "192.168.1.99" : "72.14.213.15";
                    String userAgent = isSuspicious ? "python-requests/2.31.0" : "Mozilla/5.0";
                    int reactionTimeMs = isSuspicious ? ThreadLocalRandom.current().nextInt(10, 51) : ThreadLocalRandom.current().nextInt(400, 2001);

                    log.info("🤖 Bot '{}' bidding ${} on {} (Reaction: {}ms)",
                            botName, bidAmount, targetId, reactionTimeMs);

                    return auctionService.placeMaxBid(
                            target.id(), botName, bidAmount, ipAddress, userAgent, reactionTimeMs
                    ).onErrorResume(e -> Mono.empty());
                }).then();
    }
}
