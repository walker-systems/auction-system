package com.walker.bidding.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walker.bidding.auction.Auction;
import com.walker.bidding.auction.AuctionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
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
    private List<CatalogItem> catalog;

    private volatile boolean isSeeding = true;

    public boolean isSeeding() {
        return this.isSeeding;
    }

    public record CatalogItem(String itemId, double startingPrice) {}

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("auctions_catalog.json").getInputStream();
            catalog = mapper.readValue(is, new TypeReference<List<CatalogItem>>() {});
            log.info("📦 Successfully loaded {} unique items from catalog.", catalog.size());
        } catch (Exception e) {
            log.error("Failed to load auctions_catalog.json", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        resetAndSeedDatabase().subscribe();
    }

    public Mono<Void> resetAndSeedDatabase() {
        log.info("🌱 Wiping old data and seeding database with 10,000 procedural auctions...");
        isSeeding = true;

        return auctionRepository.deleteAll()
                .then(seedDatabase())
                .doOnSuccess(v -> {
                    log.info("🎉 Storefront fully stocked with 10,000 items!");
                    isSeeding = false; // 👈 Mark as complete
                });
    }

    private Mono<Void> seedDatabase() {
        if (catalog == null || catalog.isEmpty()) {
            isSeeding = false;
            return Mono.empty();
        }

        return Flux.range(1, 10000)
                .flatMap(i -> {
                    String id = "auc-" + i;
                    CatalogItem item = catalog.get((i - 1) % catalog.size());

                    double basePrice = Math.floor(item.startingPrice());
                    double[] cents = {0.50, 0.95, 0.99};
                    double snappedPrice = basePrice + cents[ThreadLocalRandom.current().nextInt(cents.length)];
                    BigDecimal price = BigDecimal.valueOf(snappedPrice).setScale(2, RoundingMode.HALF_UP);

                    long offsetSeconds;
                    if (i <= 3) {
                        offsetSeconds = ThreadLocalRandom.current().nextLong(30, 90);
                    } else if (i <= 24) {
                        offsetSeconds = ThreadLocalRandom.current().nextLong(300, 900);
                    } else {
                        offsetSeconds = ThreadLocalRandom.current().nextLong(3600, 86400);
                    }

                    Instant endsAt = Instant.now().plusSeconds(offsetSeconds);

                    Auction auction = new Auction(
                            id, item.itemId(), price, "System", endsAt, true, 0, null, null, 0, 0, 0
                    );
                    return auctionRepository.save(auction);
                }, 32)
                .then();
    }
}
