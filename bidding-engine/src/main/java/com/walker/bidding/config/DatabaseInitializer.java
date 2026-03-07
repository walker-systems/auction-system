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

        return auctionRepository.deleteAll()
                .thenMany(Flux.fromIterable(DEMO_ITEMS)
                        .index()
                        .flatMap(tuple -> {
                            long i = tuple.getT1();
                            String itemName = tuple.getT2();

                            long startingPrice = 50 + ThreadLocalRandom.current().nextInt(450);

                            int randomSeconds = ThreadLocalRandom.current().nextInt(30, 120);
                            Instant staggeredExpiration = Instant.now()
                                    .plus(Duration.ofSeconds(randomSeconds));

                            Auction newAuction = new Auction(
                                    "auc-" + i,
                                    itemName,
                                    BigDecimal.valueOf(startingPrice),
                                    "System",
                                    staggeredExpiration,
                                    true,
                                    0,
                                    null,
                                    null,
                                    0,
                                    0,
                                    0
                            );
                            return auctionRepository.save(newAuction);
                        })
                )
                .doOnComplete(() -> log.info("🎉 Storefront fully stocked!"))
                .then();
    }}
