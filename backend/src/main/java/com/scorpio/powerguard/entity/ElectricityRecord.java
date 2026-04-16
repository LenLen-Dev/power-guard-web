package com.scorpio.powerguard.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ElectricityRecord {

    private Long id;
    private Long roomId;
    private BigDecimal remainSnapshot;
    private LocalDateTime fetchTime;
}
