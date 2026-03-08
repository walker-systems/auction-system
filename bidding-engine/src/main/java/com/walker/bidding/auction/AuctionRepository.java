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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.math.BigDecimal;

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

    public Mono<Auction> placeProxyBid(String auctionId, String bidderId, BigDecimal maxBid,
                                       String ipAddress, String userAgent, int reactionTimeMs,
                                       int simulatedBidCount, int simulatedNewIp) {

        String lua = """
                local auctionKey = KEYS[1]
                local maxBidsKey = KEYS[2]
                
                local bidderId = ARGV[1]
                local maxBid = tonumber(ARGV[2])
                local ipAddress = ARGV[3]
                local userAgent = ARGV[4]
                local reactionTimeMs = tonumber(ARGV[5])
                local simulatedBidCount = tonumber(ARGV[6])
                local simulatedNewIp = tonumber(ARGV[7])
                
                local auctionJson = redis.call('GET', auctionKey)
                if not auctionJson then return '{"error":"Auction not found"}' end
                local auction = cjson.decode(auctionJson)
                
                if not auction.active then return '{"error":"Auction is closed"}' end
                
                local function get_increment(price)
                    if price < 10.0 then return 0.50
                    elseif price < 50.0 then return 1.00
                    elseif price < 100.0 then return 5.00
                    elseif price < 500.0 then return 10.00
                    elseif price < 1000.0 then return 25.00
                    else return 50.00 end
                end
                
                local currentPrice = tonumber(auction.currentPrice)
                local minNextBid = currentPrice + get_increment(currentPrice)
                
                if auction.highBidder ~= bidderId and maxBid < minNextBid then
                    return '{"error":"Bid must be at least $' .. string.format("%.2f", minNextBid) .. '"}'
                end
                
                local existingMax = tonumber(redis.call('ZSCORE', maxBidsKey, bidderId) or 0)
                if maxBid <= existingMax then
                    return '{"error":"Max bid must be higher than your current max bid"}'
                end
                
                redis.call('ZADD', maxBidsKey, maxBid, bidderId)
                
                local top2 = redis.call('ZREVRANGE', maxBidsKey, 0, 1, 'WITHSCORES')
                local highestBidder = top2[1]
                local highestMax = tonumber(top2[2])
                local newVisiblePrice = currentPrice
                
                if #top2 == 2 then
                    newVisiblePrice = currentPrice
                elseif #top2 == 4 then
                    local secondHighestMax = tonumber(top2[4])
                    local incrementedSecond = secondHighestMax + get_increment(secondHighestMax)
                
                    if incrementedSecond > highestMax then
                        newVisiblePrice = highestMax
                    else
                        newVisiblePrice = incrementedSecond
                    end
                end
                
                auction.highBidder = highestBidder
                auction.currentPrice = newVisiblePrice
                auction.version = tonumber(auction.version) + 1
                
                auction.ipAddress = ipAddress
                auction.userAgent = userAgent
                auction.reactionTimeMs = reactionTimeMs
                auction.bidCountLastMin = simulatedBidCount
                auction.isNewIp = simulatedNewIp
                
                local updatedJson = cjson.encode(auction)
                redis.call('SET', auctionKey, updatedJson)
                
                return updatedJson
                """;

        return stringTemplate.execute(
                RedisScript.of(lua, String.class),
                List.of(getKey(auctionId), getKey(auctionId) + ":max_bids"), // KEYS[1], KEYS[2]
                List.of(
                        bidderId,
                        maxBid.toString(),
                        ipAddress != null ? ipAddress : "",
                        userAgent != null ? userAgent : "",
                        String.valueOf(reactionTimeMs),
                        String.valueOf(simulatedBidCount),
                        String.valueOf(simulatedNewIp)
                )
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
                return Mono.error(new RuntimeException("Failed to deserialize proxy bid result", e));
            }
        });
    }

    public Mono<Auction> revertFraudulentBid(String auctionId, String fraudUser, BigDecimal fallbackBasePrice) {
        String lua = """
                local auctionKey = KEYS[1]
                local maxBidsKey = KEYS[2]
                local fraudUser = ARGV[1]
                local fallbackPrice = tonumber(ARGV[2])
                
                local auctionJson = redis.call('GET', auctionKey)
                if not auctionJson then return '{"error":"Auction not found"}' end
                local auction = cjson.decode(auctionJson)
                
                redis.call('ZREM', maxBidsKey, fraudUser)
                
                local function get_increment(price)
                    if price < 10.0 then return 0.50
                    elseif price < 50.0 then return 1.00
                    elseif price < 100.0 then return 5.00
                    elseif price < 500.0 then return 10.00
                    elseif price < 1000.0 then return 25.00
                    else return 50.00 end
                end
                
                local top2 = redis.call('ZREVRANGE', maxBidsKey, 0, 1, 'WITHSCORES')
                local highestBidder = cjson.null
                local newVisiblePrice = fallbackPrice
                if #top2 > 0 then
                    highestBidder = top2[1]
                    local highestMax = tonumber(top2[2])
                
                    if #top2 == 4 then
                        local secondHighestMax = tonumber(top2[4])
                        local incrementedSecond = secondHighestMax + get_increment(secondHighestMax)
                
                        if incrementedSecond > highestMax then
                            newVisiblePrice = highestMax
                        else
                            newVisiblePrice = incrementedSecond
                        end
                    end
                end
                
                auction.highBidder = highestBidder
                auction.currentPrice = newVisiblePrice
                auction.version = tonumber(auction.version) + 1
                
                auction.ipAddress = cjson.null
                auction.userAgent = cjson.null
                auction.reactionTimeMs = 0
                auction.bidCountLastMin = 0
                auction.isNewIp = 0
                
                local updatedJson = cjson.encode(auction)
                redis.call('SET', auctionKey, updatedJson)
                
                return updatedJson
                """;

        return stringTemplate.execute(
                RedisScript.of(lua, String.class),
                List.of(getKey(auctionId), getKey(auctionId) + ":max_bids"),
                List.of(fraudUser, fallbackBasePrice.toString()) // Pass it here!
        ).next().flatMap(result -> {
            if (result.startsWith("{\"error\"")) {
                String errMsg = result.split("\"")[3];
                return Mono.error(new IllegalArgumentException(errMsg));
            }
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                return Mono.just(mapper.readValue(result, Auction.class));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to deserialize rollback result", e));
            }
        });
    }
}
