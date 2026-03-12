package com.walker.bidding.config;

import com.walker.bidding.auction.Auction;
import com.walker.bidding.auction.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private final AuctionRepository auctionRepository;

    private static final List<String> ADJECTIVES = List.of(
            "Vintage", "Refurbished", "Brand New", "Limited Edition", "Signed",
            "Antique", "Factory Sealed", "Custom", "Mint Condition", "Rare"
    );
    private static final List<String> BRANDS = List.of(
            "Sony", "Apple", "NVIDIA", "Herman Miller", "La Marzocco",
            "Rolex", "Samsung", "LG", "Nike", "Tesla", "Omega", "Nintendo"
    );
    private static final List<String> NOUNS = List.of(
            "Monitor", "Keyboard", "GPU", "Chair", "Espresso Machine",
            "Watch", "Smartphone", "Laptop", "Sneakers", "Hoverboard", "Camera"
    );

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        resetAndSeedDatabase().subscribe();
    }

    public Mono<Void> resetAndSeedDatabase() {
        log.info("🌱 Wiping old data and seeding database with 10,000 procedural auctions...");
        return auctionRepository.deleteAll()
                .then(seedDatabase())
                .doOnSuccess(v -> log.info("🎉 Storefront fully stocked with 10,000 items!"));
    }

    private Mono<Void> seedDatabase() {
        return Flux.range(1, 10000)
                .flatMap(i -> {
                    String id = "auc-" + i;
                    String name = generateName();

                    double randomPrice = 10.0 + (990.0 * ThreadLocalRandom.current().nextDouble());
                    BigDecimal price = BigDecimal.valueOf(randomPrice).setScale(2, RoundingMode.HALF_UP);

                    long offsetSeconds = ThreadLocalRandom.current().nextLong(3600, 86400);
                    Instant endsAt = Instant.now().plusSeconds(offsetSeconds);

                    Auction auction = new Auction(
                            id, name, price, "System", endsAt, true, 0, null, null, 0, 0, 0
                    );
                    return auctionRepository.save(auction);
                }, 256)
                .then();
    }
    private String generateName() {
        String adj = ADJECTIVES.get(ThreadLocalRandom.current().nextInt(ADJECTIVES.size()));
        String brand = BRANDS.get(ThreadLocalRandom.current().nextInt(BRANDS.size()));
        String noun = NOUNS.get(ThreadLocalRandom.current().nextInt(NOUNS.size()));
        return adj + " " + brand + " " + noun;
    }
}
