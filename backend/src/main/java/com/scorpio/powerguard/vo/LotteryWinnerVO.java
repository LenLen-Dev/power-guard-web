package com.scorpio.powerguard.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LotteryWinnerVO {

    private Integer winnerRank;
    private String buildingName;
    private String roomId;
    private String roomName;
    private BigDecimal rewardAmount;
}
