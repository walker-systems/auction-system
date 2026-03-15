package com.walker.bidding.auction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.math.BigDecimal;

@Repository
@Slf4j
public class AuctionRepository {
    
    private static final String KEY_PREFIX_AUCTION = "auctions:";
    private static final String KEY_SUFFIX_MAX_BIDS = ":max_bids";

    private static final String KEY_ACTIVE_AUCTIONS = "active_auctions";
    private static final String KEY_BANNED_USERS = "banned_users";

    private static final String CHANNEL_PREFIX_UPDATES = "auction:updates:";

    // Templates defined in config/RedisConfig.java are injected by Spring
    private final ReactiveRedisTemplate<String, Auction> auctionTemplate;
    private final ReactiveRedisTemplate<String, String> stringTemplate;
    
    private final DefaultRedisScript<Boolean> updateAuctionLua;
    private final DefaultRedisScript<String> revertBidLua;

    // Manual constructor necessary to load Lua scripts (can't use Lombok here).
    public AuctionRepository(ReactiveRedisTemplate<String, Auction> auctionTemplate,
                             ReactiveRedisTemplate<String, String> stringTemplate) {
        this.auctionTemplate = auctionTemplate;
        this.stringTemplate = stringTemplate;

        this.updateAuctionLua = new DefaultRedisScript<>();
        this.updateAuctionLua.setLocation(new ClassPathResource("scripts/update_auction.lua"));
        this.updateAuctionLua.setResultType(Boolean.class);

        this.revertBidLua = new DefaultRedisScript<>();
        this.revertBidLua.setLocation(new ClassPathResource("scripts/revert_bid.lua"));
        this.revertBidLua.setResultType(String.class);
    }

    // Example = "auctions:auc-123"
    private String addPrefix(String id) {
        return KEY_PREFIX_AUCTION + id;
    }

    public Mono<Auction> save(Auction auction) {
        return auctionTemplate.opsForValue()
                .set(addPrefix(auction.id()), auction)
                .then(updateActiveAuctionIndex(auction))
                .thenReturn(auction);
    }

    public Mono<Auction> findById(String id) {
        return auctionTemplate.opsForValue().get(addPrefix(id));
    }

    public Mono<Boolean> updateAuction(Auction proposedAuction) {
        return auctionTemplate.execute(
                updateAuctionLua,

                // KEYS[1] = "auctions:auc-123"
                // KEYS[2] = "active_auctions"
                List.of(addPrefix(proposedAuction.id()), KEY_ACTIVE_AUCTIONS),

                // ARGV[1]  = JSON string representing auction object.
                List.of(proposedAuction)
        ).next();
    }

    public Mono<Long> publishUpdate(Auction auction) {

        // Publishes to Channel: "auction:updates:auc-123"
        return auctionTemplate.convertAndSend(CHANNEL_PREFIX_UPDATES + auction.id(), auction);
    }

    public Flux<Auction> observeAuctionUpdates(String auctionId) {

        // Subscribes to Channel: "auction:updates:auc-123"
        return auctionTemplate.listenTo(ChannelTopic.of(CHANNEL_PREFIX_UPDATES + auctionId))
                .map(Message::getMessage);
    }

    public Flux<Auction> findAll() {

        // Scans Set Key: "active_auctions"
        return stringTemplate.opsForSet()
                .members(KEY_ACTIVE_AUCTIONS)
                .flatMap(this::findById);
    }

    public Mono<Void> deleteAll() {
        log.info("🗑️ Sweeping the database clean via non-blocking SCAN...");

        // Matches Pattern: "auctions:*" (e.g., "auctions:auc-1", "auctions:auc-2")
        ScanOptions options = ScanOptions.scanOptions()
                .match("auctions:*")
                .count(100)
                .build();

        return stringTemplate.scan(options)
                .flatMap(auctionTemplate::delete)

                // Deletes empty collection "active_auctions"
                .then(stringTemplate.delete(KEY_ACTIVE_AUCTIONS))

                // Deletes empty collection "banned_users"
                .then(stringTemplate.delete(KEY_BANNED_USERS))
                .then();
    }

    private Mono<Long> updateActiveAuctionIndex(Auction auction) {

        // Adds auction to active auctions index if it's currently active. Otherwise, it is removed.
        if (auction.active()) {
            return stringTemplate.opsForSet().add(KEY_ACTIVE_AUCTIONS, auction.id());
        }
        return stringTemplate.opsForSet().remove(KEY_ACTIVE_AUCTIONS, auction.id());
    }

    public Mono<Auction> revertBid(String auctionId, String fraudUser, BigDecimal fallbackBasePrice) {
        // Passes to Lua:
        // KEYS[1] = "auctions:auc-123"
        // KEYS[2] = "auctions:auc-123:max_bids"
        return stringTemplate.execute(
                revertBidLua,
                List.of(addPrefix(auctionId), addPrefix(auctionId) + KEY_SUFFIX_MAX_BIDS),
                List.of(fraudUser, fallbackBasePrice.toString())
        ).next().flatMap(result -> {
            if (result.startsWith("{\"error\"")) {
                String errMsg = result.split("\"")[3];
                return Mono.error(new IllegalArgumentException(errMsg));
            }
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                return Mono.just(mapper.readValue(result, Auction.class));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to deserialize rollback result", e));
            }
        });
    }
}
