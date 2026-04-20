package com.scorpio.powerguard.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LotteryDraw {

    private Long id;
    private String drawKey;
    private LocalDateTime drawTime;
    private Integer winnerCount;
    private String message;
    private LocalDateTime createTime;
}
