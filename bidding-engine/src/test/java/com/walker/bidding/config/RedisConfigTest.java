package com.walker.bidding.config;

import com.walker.bidding.auction.Auction;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    @Test
    void serializeAndDeserialize_PreservesComplexObjectsAndTimestamps() {
        RedisConfig config = new RedisConfig();

        RedisSerializer<Auction> serializer = config.auctionSerializer();

        Instant exactTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Auction original = new Auction(
                "auc-test-1", "item-x", new BigDecimal("10.50"), "System",
                exactTime, true, 0, null, null, 0, 0, 0
        );

        byte[] bytes = serializer.serialize(original);
        Auction restored = serializer.deserialize(bytes);

        assertNotNull(bytes, "Serialized bytes should not be null");
        assertTrue(bytes.length > 0, "Serialized bytes should contain data");
        assertNotNull(restored, "Restored auction should not be null");

        assertEquals(original.id(), restored.id());
        assertEquals(original.currentPrice(), restored.currentPrice());
        assertEquals(original.endsAt().toEpochMilli(), restored.endsAt().toEpochMilli(), "Timestamps must match perfectly!");
    }

    @Test
    void deserialize_ReturnsNullOnEmptyBytes() {
        RedisConfig config = new RedisConfig();
        RedisSerializer<Auction> serializer = config.auctionSerializer();

        assertNull(serializer.deserialize(new byte[0]), "Empty bytes should return null");
        assertNull(serializer.deserialize(null), "Null bytes should return null");
    }
}
