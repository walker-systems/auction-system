package com.walker.bidding.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walker.bidding.auction.Auction;
import com.walker.bidding.auction.AuctionRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
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

    @Getter
    private volatile boolean isSeeding = true;

    // small record only used here to transfer items from auctions_catalog.json to Redis
    // startingPrice is converted further below to BigDecimal "price" before additional processing - kept double here
    // for small speed boost
    public record CatalogItem(String itemId, double startingPrice) {}

    // immediately fail boot process if auctions_catalog.json is missing
    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream iS = new ClassPathResource("auctions_catalog.json").getInputStream();

            // anonymous inner class so Jackson recognizes elements of list as CatalogItem
            catalog = mapper.readValue(iS, new TypeReference<List<CatalogItem>>() {});
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
                .doOnSuccess(_ -> {
                    log.info("🎉 Storefront fully stocked with 10,000 items!");
                    isSeeding = false;
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

                    // conversion of double startingPrice to BigDecimal
                    BigDecimal price = BigDecimal.valueOf(item.startingPrice()).setScale(2, RoundingMode.HALF_UP);
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
                }, 32) // process 32 items at a time to prevent Droplet crash
                .then()
                .doFinally(_ -> {
                    isSeeding = false;
                });
    }}
