package com.walkersystems.sentinel;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FraudCheckResponse(
        @JsonProperty("fraud_probability") double fraudProbability,
        @JsonProperty("is_fraud") boolean isFraud
) {}
