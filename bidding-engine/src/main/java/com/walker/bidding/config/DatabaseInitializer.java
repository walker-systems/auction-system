package com.walker.bidding.config;

import com.walker.bidding.auction.Auction;
import com.walker.bidding.auction.AuctionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final AuctionRepository auctionRepository;

    private final List<String> DEMO_ITEMS = List.of(
            "Sony PlayStation 5 Pro", "Apple MacBook Pro M3 Max",
            "Vintage 1999 Holographic Charizard", "Herman Miller Aeron Chair",
            "NVIDIA RTX 4090 GPU", "Signed Michael Jordan Baseball",
            "Onewheel Onewheel+ XR", "Espresso Machine - La Marzocco"
    );

    @Override
    public void run(String... args) {
        resetAndSeedDatabase().subscribe();
    }

    public Mono<Void> resetAndSeedDatabase() {
        log.info("🌱 Wiping old data and seeding database with realistic auctions...");

        // 1. Delete all existing auctions first to prevent duplicates!
        return auctionRepository.deleteAll()
                .thenMany(Flux.fromIterable(DEMO_ITEMS)
                        .index() // <-- This adds a sequential number to our stream
                        .flatMap(tuple -> {
                            long i = tuple.getT1();          // The sequential index (0, 1, 2...)
                            String itemName = tuple.getT2(); // The actual item name from DEMO_ITEMS

                            long startingPrice = 50 + ThreadLocalRandom.current().nextInt(450);

                            Auction newAuction = new Auction(
                                    "auc-" + i,
                                    itemName, // Use the real item name
                                    BigDecimal.valueOf(startingPrice), // Use the dynamic random price
                                    "System",
                                    Instant.now().plus(Duration.ofHours(24)),
                                    true,
                                    0,      // version
                                    null,   // ipAddress (no bid yet)
                                    null,   // userAgent (no bid yet)
                                    0,      // reactionTimeMs
                                    0,      // bidCountLastMin
                                    0       // isNewIp
                            );
                            return auctionRepository.save(newAuction);
                        })
                )
                .doOnComplete(() -> log.info("🎉 Storefront fully stocked!"))
                .then();
    }}
