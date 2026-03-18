package com.walkersystems.sentinel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FraudCheckResponse(
        @JsonProperty("id") String id,
        @JsonProperty("fraud_probability") double fraudProbability,
        @JsonProperty("is_fraud") boolean isFraud
) {}
