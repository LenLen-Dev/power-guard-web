package com.scorpio.powerguard.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LotteryDrawVO {

    private String drawKey;
    private LocalDateTime drawTime;
    private Integer winnerCount;
    private String message;
    private List<LotteryWinnerVO> winners;
}
