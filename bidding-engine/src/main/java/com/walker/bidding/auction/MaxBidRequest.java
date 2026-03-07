package com.walker.bidding.auction;

import java.math.BigDecimal;

public record MaxBidRequest(
        String bidderId,
        BigDecimal maxBid,
        Telemetry telemetry,
        String requestId
) {
    public record Telemetry(String ipAddress, String userAgent, int reactionTimeMs) {}
}
