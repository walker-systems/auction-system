package com.walker.bidding.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walker.bidding.auction.AuctionRepository;
import com.walker.bidding.auction.AuctionService;
import com.walker.bidding.auction.BidIncrementCalculator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private List<BotPersona> botSwarm;

    public record BotPersona(String bidderId, String ipAddress, String userAgent, int baseReactionTimeMs, boolean isMalicious) {}

    @PostConstruct
    public void init() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream is = new ClassPathResource("bot_swarm.json").getInputStream();
            botSwarm = objectMapper.readValue(is, new TypeReference<List<BotPersona>>() {});
            log.info("🤖 Successfully loaded {} unique bots into memory.", botSwarm.size());
        } catch (Exception e) {
            log.error("Failed to load bot_swarm.json. Bots will not start.", e);
        }
    }

    public synchronized void startBotSwarm() {
        if (botTask != null && !botTask.isDisposed()) {
            log.info("Bots are already running!");
            return;
        }
        log.info("🤖 Demo Bot Swarm ACTIVATED!");

        botTask = Flux.interval(Duration.ofMillis(100), Duration.ofMillis(250))
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
        if (botSwarm == null || botSwarm.isEmpty()) return Mono.empty();

        int roll = ThreadLocalRandom.current().nextInt(100);
        int targetNum = (roll < 85)
                ? ThreadLocalRandom.current().nextInt(1, 25)
                : ThreadLocalRandom.current().nextInt(25, 10001);

        String targetId = "auc-" + targetNum;

        return auctionRepository.findById(targetId)
                .flatMap(target -> {
                    if (!target.active()) return Mono.empty();

                    BotPersona bot = botSwarm.get(ThreadLocalRandom.current().nextInt(botSwarm.size()));

                    BigDecimal minIncrement = BidIncrementCalculator.getIncrement(target.currentPrice());
                    double multiplier = 1.0 + ThreadLocalRandom.current().nextInt(5);

                    BigDecimal bidAmount = target.currentPrice()
                            .add(minIncrement.multiply(BigDecimal.valueOf(multiplier)))
                            .setScale(2, RoundingMode.HALF_UP);

                    log.info("🤖 Bot '{}' bidding ${} on {} (Reaction: {}ms)",
                            bot.bidderId(), bidAmount, targetId, bot.baseReactionTimeMs());

                    return auctionService.placeMaxBid(
                            target.id(),
                            bot.bidderId(),
                            bidAmount,
                            bot.ipAddress(),
                            bot.userAgent(),
                            bot.baseReactionTimeMs()
                    ).onErrorResume(e -> Mono.empty());
                }).then();
    }
}
