package com.scorpio.powerguard.util;

import java.math.BigDecimal;

public final class ElectricityTotalResolver {

    private ElectricityTotalResolver() {
    }

    public static BigDecimal resolve(BigDecimal latestRemain, BigDecimal previousRemain, BigDecimal previousTotal) {
        if (latestRemain == null) {
            return previousTotal;
        }
        if (previousRemain == null || previousTotal == null) {
            return latestRemain;
        }
        if (latestRemain.compareTo(previousRemain) > 0) {
            return latestRemain;
        }
        return previousTotal;
    }
}
