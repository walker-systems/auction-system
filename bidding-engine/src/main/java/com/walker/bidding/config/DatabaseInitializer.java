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

    // Extracted into a method so the Reset button can call it
    public Mono<Void> resetAndSeedDatabase() {
        log.info("🌱 Wiping old data and seeding database with realistic auctions...");

        // 1. Delete all existing auctions first to prevent duplicates!
        return auctionRepository.deleteAll()
                .thenMany(Flux.fromIterable(DEMO_ITEMS)
                        .flatMap(itemName -> {
                            long startingPrice = 50 + ThreadLocalRandom.current().nextInt(450);
                            int randomSeconds = ThreadLocalRandom.current().nextInt(30, 181);

                            Auction newAuction = new Auction(
                                    "auction-" + UUID.randomUUID().toString().substring(0, 8),
                                    itemName,
                                    BigDecimal.valueOf(startingPrice),
                                    "System",
                                    Instant.now().plusSeconds(randomSeconds),
                                    true,
                                    1
                            );
                            return auctionRepository.save(newAuction);
                        })
                )
                .doOnComplete(() -> log.info("🎉 Storefront fully stocked!"))
                .then();
    }
}
