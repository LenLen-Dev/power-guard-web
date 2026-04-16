package com.scorpio.powerguard.util;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectricityTotalResolverTest {

    @Test
    void shouldUseLatestRemainWhenPreviousSnapshotMissing() {
        BigDecimal resolved = ElectricityTotalResolver.resolve(new BigDecimal("46.19"), null, null);

        assertEquals(new BigDecimal("46.19"), resolved);
    }

    @Test
    void shouldUseLatestRemainWhenRechargeDetected() {
        BigDecimal resolved = ElectricityTotalResolver.resolve(
            new BigDecimal("30"),
            new BigDecimal("12"),
            new BigDecimal("60")
        );

        assertEquals(new BigDecimal("30"), resolved);
    }

    @Test
    void shouldKeepPreviousTotalWhenRemainDoesNotIncrease() {
        BigDecimal resolved = ElectricityTotalResolver.resolve(
            new BigDecimal("25"),
            new BigDecimal("30"),
            new BigDecimal("60")
        );

        assertEquals(new BigDecimal("60"), resolved);
    }
}
