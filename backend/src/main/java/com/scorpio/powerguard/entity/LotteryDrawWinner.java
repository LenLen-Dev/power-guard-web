package com.scorpio.powerguard.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LotteryDrawWinner {

    private Long id;
    private Long drawId;
    private Long roomPkId;
    private Integer winnerRank;
    private String buildingId;
    private String buildingName;
    private String roomId;
    private String roomName;
    private String alertEmail;
    private BigDecimal rewardAmount;
    private LocalDateTime createTime;
}
