package com.walker.bidding.auction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Repository
@Slf4j
public class AuctionRepository {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String KEY_PREFIX_AUCTION = "auctions:";
    private static final String KEY_SUFFIX_MAX_BIDS = ":max_bids";

    private static final String KEY_ACTIVE_AUCTIONS = "active_auctions";
    private static final String KEY_BANNED_USERS = "banned_users";

    private static final String CHANNEL_PREFIX_UPDATES = "auction:updates:";

    private final ReactiveRedisTemplate<String, Auction> auctionTemplate;
    private final ReactiveRedisTemplate<String, String> stringTemplate;

    private final DefaultRedisScript<Long> updateAuctionLua;
    private final DefaultRedisScript<String> revertBidLua;

    public AuctionRepository(ReactiveRedisTemplate<String, Auction> auctionTemplate,
                             ReactiveRedisTemplate<String, String> stringTemplate) {
        this.auctionTemplate = auctionTemplate;
        this.stringTemplate = stringTemplate;

        this.updateAuctionLua = new DefaultRedisScript<>();
        this.updateAuctionLua.setLocation(new ClassPathResource("scripts/update_auction.lua"));
        this.updateAuctionLua.setResultType(Long.class);

        this.revertBidLua = new DefaultRedisScript<>();
        this.revertBidLua.setLocation(new ClassPathResource("scripts/revert_bid.lua"));
        this.revertBidLua.setResultType(String.class);
    }

    private String buildAuctionKey(String id) {
        return KEY_PREFIX_AUCTION + id;
    }

    public Mono<Auction> save(Auction auction) {
        return auctionTemplate.opsForValue()
                .set(buildAuctionKey(auction.id()), auction)
                .then(addToActiveAuctions(auction))
                .then(stringTemplate.opsForZSet().add("auction:expirations", auction.id(), (double) auction.endsAt().toEpochMilli()))
                .thenReturn(auction);
    }

    public Mono<Auction> findById(String id) {
        return auctionTemplate.opsForValue().get(buildAuctionKey(id));
    }

    public Mono<Boolean> updateAuction(Auction updatedAuction) {
        String auctionKey = buildAuctionKey(updatedAuction.id());
        String zSetKey = auctionKey + ":bids";

        List<String> keys = List.of(auctionKey, zSetKey);
        long expectedVersion = updatedAuction.version() - 1;

        List<String> args = List.of(
                String.valueOf(updatedAuction.currentPrice()),
                updatedAuction.highBidder(),
                String.valueOf(expectedVersion),
                String.valueOf(updatedAuction.endsAt().toEpochMilli() / 1000.0)
        );

        return stringTemplate.execute(updateAuctionLua, keys, args)
                .next()
                .map(newVersion -> newVersion != -1L);
    }

    public Mono<Long> publishUpdate(Auction auction) {
        return auctionTemplate.convertAndSend(CHANNEL_PREFIX_UPDATES + auction.id(), auction)
                .then(Mono.defer(() -> {
                    try {
                        return stringTemplate.opsForStream().add(
                                "stream:auction:updates",
                                java.util.Map.of("auction", objectMapper.writeValueAsString(auction))
                        );
                    } catch (Exception e) {
                        log.error("Failed to serialize auction for stream", e);
                        return Mono.empty();
                    }
                }))
                .thenReturn(1L);
    }

    public Flux<Auction> observeAuctionUpdates(String auctionId) {
        return auctionTemplate.listenTo(ChannelTopic.of(CHANNEL_PREFIX_UPDATES + auctionId))
                .map(Message::getMessage);
    }

    public Flux<Auction> findAll() {
        return stringTemplate.opsForSet()
                .members(KEY_ACTIVE_AUCTIONS)
                .flatMap(this::findById);
    }

    public Mono<Void> deleteAll() {
        log.info("🗑️ Sweeping the database clean via non-blocking SCAN...");

        ScanOptions options = ScanOptions.scanOptions()
                .match("auctions:*")
                .count(100)
                .build();

        return stringTemplate.scan(options)
                .flatMap(auctionTemplate::delete)
                .then(stringTemplate.delete(KEY_ACTIVE_AUCTIONS))
                .then(stringTemplate.delete(KEY_BANNED_USERS))
                .then(stringTemplate.delete("auction:expirations"))
                .then();
    }

    private Mono<Long> addToActiveAuctions(Auction auction) {
        if (auction.active()) {
            return stringTemplate.opsForSet().add(KEY_ACTIVE_AUCTIONS, auction.id());
        }
        return stringTemplate.opsForSet().remove(KEY_ACTIVE_AUCTIONS, auction.id());
    }

    public Mono<Auction> revertBid(String auctionId, String fraudUser, BigDecimal fallbackBasePrice) {
        return stringTemplate.execute(
                revertBidLua,
                List.of(buildAuctionKey(auctionId), buildAuctionKey(auctionId) + KEY_SUFFIX_MAX_BIDS),
                List.of(fraudUser, fallbackBasePrice.toString())
        ).next().flatMap(result -> {
            if (result.startsWith("{\"error\"")) {
                String errMsg = result.split("\"")[3];
                return Mono.error(new IllegalArgumentException(errMsg));
            }
            try {
                return Mono.just(objectMapper.readValue(result, Auction.class));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to deserialize rollback result", e));
            }
        });
    }

    public Flux<Auction> observeAllAuctionUpdates() {
        return auctionTemplate.listenTo(PatternTopic.of(CHANNEL_PREFIX_UPDATES + "*"))
                .map(Message::getMessage);
    }
}
