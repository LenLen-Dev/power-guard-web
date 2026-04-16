package com.scorpio.powerguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scorpio.powerguard.util.RoomStatusCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RoomStatusCalculatorTest {

    @Test
    void shouldReturnNormalWhenRemainIsGreaterThanThresholdPlusTen() {
        Integer status = RoomStatusCalculator.calculate(new BigDecimal("31"), new BigDecimal("20"));
        assertEquals(0, status);
    }

    @Test
    void shouldReturnWarningWhenRemainIsWithinThresholdPlusTen() {
        Integer status = RoomStatusCalculator.calculate(new BigDecimal("25"), new BigDecimal("20"));
        assertEquals(1, status);
    }

    @Test
    void shouldReturnAlertWhenRemainIsLessThanOrEqualThreshold() {
        Integer status = RoomStatusCalculator.calculate(new BigDecimal("20"), new BigDecimal("20"));
        assertEquals(2, status);
    }
}
