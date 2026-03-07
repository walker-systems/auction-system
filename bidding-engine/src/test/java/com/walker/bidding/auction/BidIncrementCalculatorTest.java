package com.walker.bidding.auction;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BidIncrementCalculatorTest {

    @Test
    void testIncrementsByTier() {
        assertEquals(new BigDecimal("0.50"), BidIncrementCalculator.getIncrement(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("1.00"), BidIncrementCalculator.getIncrement(new BigDecimal("25.00")));
        assertEquals(new BigDecimal("5.00"), BidIncrementCalculator.getIncrement(new BigDecimal("75.00")));
        assertEquals(new BigDecimal("10.00"), BidIncrementCalculator.getIncrement(new BigDecimal("250.00")));
        assertEquals(new BigDecimal("25.00"), BidIncrementCalculator.getIncrement(new BigDecimal("750.00")));
        assertEquals(new BigDecimal("50.00"), BidIncrementCalculator.getIncrement(new BigDecimal("1500.00")));
    }

    @Test
    void testMinimumNextBid() {
        assertEquals(new BigDecimal("80.00"), BidIncrementCalculator.getMinimumNextBid(new BigDecimal("75.00")));
    }
}
