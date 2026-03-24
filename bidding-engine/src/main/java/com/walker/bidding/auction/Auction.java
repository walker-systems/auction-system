package com.walker.bidding.auction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RedisHash("auctions")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Auction(
        @Id String id,
        String itemId,
        BigDecimal currentPrice,
        String highBidder,
        Instant endsAt,
        boolean active,
        int version,

        String ipAddress,
        String userAgent,
        int reactionTimeMs,
        int bidCountLastMin,
        int isNewIp
) {
    public Auction {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (currentPrice == null) {
            currentPrice = BigDecimal.ZERO;
        }
    }
}
