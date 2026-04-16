package com.scorpio.powerguard.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class DailyTrendVO {

    private LocalDate date;
    private BigDecimal startRemain;
    private BigDecimal endRemain;
    private BigDecimal consumption;
    private Boolean rechargeDetected;
    private Boolean dataComplete;
}
