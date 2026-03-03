package com.walkersystems.sentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuctionDto(
        String id,
        String itemId,
        String highBidder,
        boolean active
) {}
