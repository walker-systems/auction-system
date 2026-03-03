package com.walker.bidding.auction;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Repository
@RequiredArgsConstructor
public class AuctionRepository {

    private final ReactiveRedisTemplate<String, Auction> template;

    private String getKey(String auctionId) {
        return "auctions:" + auctionId;
    }

    public Mono<Auction> save(Auction auction) {
        return template.opsForValue()
                .set(getKey(auction.id()), auction)
                .thenReturn(auction);
    }

    public Mono<Auction> findById(String id) {
        return template.opsForValue().get(getKey(id));
    }

    // Atomically updates auction only if version matches.
    public Mono<Boolean> updateWithVersion(Auction proposedAuction) {
        String lua = """
                local proposedAuctionKey = KEYS[1]
                local proposedAuctionJson = ARGV[1]
                
                local databaseAuctionJson = redis.call('GET', proposedAuctionKey)
                if not databaseAuctionJson then return false end
                
                local databaseAuction = cjson.decode(databaseAuctionJson)
                local proposedAuction = cjson.decode(proposedAuctionJson)
                local auctionKey = proposedAuctionKey
                
                if tonumber(databaseAuction.version) == (tonumber(proposedAuction.version) - 1) then
                    redis.call('SET', auctionKey, proposedAuctionJson)
                    return true
                else
                    return false
                end
                """;

        return template.execute(
                RedisScript.of(lua, Boolean.class),
                List.of(getKey(proposedAuction.id())), // KEYS[1]
                List.of(proposedAuction) // ARGV[1]
        ).next();
    }

    public Mono<Long> publishUpdate(Auction auction) {
        return template.convertAndSend("auction:updates", auction);
    }

    public Flux<Auction> observeAuctionUpdates() {
        return template.listenTo(ChannelTopic.of("auction:updates"))
                .map(Message::getMessage);
    }

    // TODO: Tech Debt - Replace template.keys() with Redis SCAN command or maintain a separate Redis Set of active auction IDs to avoid blocking the DB in prod.
    public Flux<Auction> findAll() {
        // Find all keys starting with "auctions:", then fetch the value for each key
        return template.keys("auctions:*")
                .flatMap(key -> template.opsForValue().get(key));
    }

    public Mono<Void> deleteAll() {
        log.info("🗑️ Sweeping the database clean...");
        // Find all keys that start with "auctions:" and delete them
        return template.keys("auctions:*")
                .flatMap(template::delete)
                .then();
    }
}
