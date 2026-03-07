package com.walker.bidding.auction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AuctionRepository {

    private static final String ACTIVE_AUCTIONS_KEY = "active_auctions";

    private final ReactiveRedisTemplate<String, Auction> template;
    private final ReactiveRedisTemplate<String, String> stringTemplate;

    private String getKey(String auctionId) {
        return "auctions:" + auctionId;
    }

    public Mono<Auction> save(Auction auction) {
        return template.opsForValue()
                .set(getKey(auction.id()), auction)
                .then(updateActiveAuctionIndex(auction))
                .thenReturn(auction);
    }

    public Mono<Auction> findById(String id) {
        return template.opsForValue().get(getKey(id));
    }

    public Mono<Boolean> updateWithVersion(Auction proposedAuction) {
        String lua = """
                local proposedAuctionKey = KEYS[1]
                local activeAuctionsKey = KEYS[2]
                local proposedAuctionJson = ARGV[1]
                
                local databaseAuctionJson = redis.call('GET', proposedAuctionKey)
                if not databaseAuctionJson then return false end
                
                local databaseAuction = cjson.decode(databaseAuctionJson)
                local proposedAuction = cjson.decode(proposedAuctionJson)
                local auctionKey = proposedAuctionKey
                
                if tonumber(databaseAuction.version) == (tonumber(proposedAuction.version) - 1) then
                    redis.call('SET', auctionKey, proposedAuctionJson)
                
                    -- Atomically update the Active Index to prevent phantom reads
                    if proposedAuction.active then
                        redis.call('SADD', activeAuctionsKey, proposedAuction.id)
                    else
                        redis.call('SREM', activeAuctionsKey, proposedAuction.id)
                    end
                
                    return true
                else
                    return false
                end
                """;

        return template.execute(
                RedisScript.of(lua, Boolean.class),
                List.of(getKey(proposedAuction.id()), ACTIVE_AUCTIONS_KEY),
                List.of(proposedAuction)
        ).next();
    }

    public Mono<Long> publishUpdate(Auction auction) {
        return template.convertAndSend("auction:updates:" + auction.id(), auction);
    }

    public Flux<Auction> observeAuctionUpdates(String auctionId) {
        return template.listenTo(ChannelTopic.of("auction:updates:" + auctionId))
                .map(Message::getMessage);
    }

    public Flux<Auction> findAll() {
        return stringTemplate.opsForSet()
                .members(ACTIVE_AUCTIONS_KEY)
                .flatMap(this::findById);
    }

    public Mono<Void> deleteAll() {
        log.info("🗑️ Sweeping the database clean via non-blocking SCAN...");

        ScanOptions options = ScanOptions.scanOptions()
                .match("auctions:*")
                .count(100)
                .build();

        return stringTemplate.scan(options)
                .flatMap(template::delete)
                .then(stringTemplate.delete(ACTIVE_AUCTIONS_KEY))
                .then();
    }

    private Mono<Long> updateActiveAuctionIndex(Auction auction) {
        if (auction.active()) {
            return stringTemplate.opsForSet().add(ACTIVE_AUCTIONS_KEY, auction.id());
        }
        return stringTemplate.opsForSet().remove(ACTIVE_AUCTIONS_KEY, auction.id());
    }
}
