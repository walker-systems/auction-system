package com.walker.bidding.auction;

import java.math.BigDecimal;

public class BidIncrementCalculator {

    public static BigDecimal getIncrement(BigDecimal currentPrice) {
        double price = currentPrice.doubleValue();

        if (price < 10.00) {
            return new BigDecimal("0.50");
        } else if (price < 50.00) {
            return new BigDecimal("1.00");
        } else if (price < 100.00) {
            return new BigDecimal("5.00");
        } else if (price < 500.00) {
            return new BigDecimal("10.00");
        } else if (price < 1000.00) {
            return new BigDecimal("25.00");
        } else {
            return new BigDecimal("50.00");
        }
    }

    public static BigDecimal getMinimumNextBid(BigDecimal currentPrice) {
        return currentPrice.add(getIncrement(currentPrice));
    }
}
