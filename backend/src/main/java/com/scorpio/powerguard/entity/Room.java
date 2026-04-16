package com.scorpio.powerguard.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Room {

    private Long id;
    private String buildingId;
    private String buildingName;
    private String roomId;
    private String roomName;
    private String alertEmail;
    private BigDecimal threshold;
    private BigDecimal total;
    private BigDecimal remain;
    private Integer status;
    private Integer deleted;
    private LocalDateTime updateTime;
}
