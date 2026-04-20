package com.scorpio.powerguard.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RefreshJobVO {

    private String jobId;
    private String source;
    private String status;
    private Integer totalRooms;
    private Integer completedRooms;
    private Integer successRooms;
    private Integer failedRooms;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String message;
}
