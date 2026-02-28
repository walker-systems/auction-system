package com.walker.bidding.auction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BidRequest(
        @NotBlank(message = "Username cannot be blank")
        String bidder,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Bid must be greater than zero")
        BigDecimal amount
) {}
