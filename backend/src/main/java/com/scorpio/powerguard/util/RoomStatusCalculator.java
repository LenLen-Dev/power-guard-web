package com.scorpio.powerguard.util;

import com.scorpio.powerguard.enums.RoomStatusEnum;
import java.math.BigDecimal;

public final class RoomStatusCalculator {

    private static final BigDecimal WARNING_OFFSET = BigDecimal.TEN;

    private RoomStatusCalculator() {
    }

    public static Integer calculate(BigDecimal remain, BigDecimal threshold) {
        if (remain == null || threshold == null) {
            return RoomStatusEnum.NORMAL.getCode();
        }
        if (remain.compareTo(threshold) <= 0) {
            return RoomStatusEnum.ALERT.getCode();
        }
        if (remain.compareTo(threshold.add(WARNING_OFFSET)) <= 0) {
            return RoomStatusEnum.WARNING.getCode();
        }
        return RoomStatusEnum.NORMAL.getCode();
    }
}
