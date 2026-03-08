package com.walkersystems.sentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuctionDto(
        String id,
        String itemId,
        String highBidder,
        boolean active,

        String ipAddress,
        String userAgent,
        int reactionTimeMs,
        int bidCountLastMin,
        int isNewIp,
        BigDecimal currentPrice
) {}
