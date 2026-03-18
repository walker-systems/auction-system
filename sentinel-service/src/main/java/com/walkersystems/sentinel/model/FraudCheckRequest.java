package com.walkersystems.sentinel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record FraudCheckRequest(
        @JsonProperty("id") String id,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("user_agent") String userAgent,
        @JsonProperty("reaction_time_ms") int reactionTimeMs,
        @JsonProperty("bid_count_last_min") int bidCountLastMin,
        @JsonProperty("is_new_ip") int isNewIp,
        @JsonProperty("bid_amount") BigDecimal bidAmount
) {}
