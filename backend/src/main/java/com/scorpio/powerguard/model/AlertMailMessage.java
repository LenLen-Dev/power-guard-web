package com.scorpio.powerguard.model;

import com.scorpio.powerguard.enums.MailMessageType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AlertMailMessage {

    private Long roomId;
    private MailMessageType mailType;
    private String project;
    private String roomName;
    private String buildingName;
    private String account;
    private String apiMessage;
    private String targetEmail;
    private BigDecimal remain;
    private BigDecimal todayUsage;
    private BigDecimal threshold;
    private Integer status;
    private LocalDateTime fetchTime;
    private Integer retryCount;
}
