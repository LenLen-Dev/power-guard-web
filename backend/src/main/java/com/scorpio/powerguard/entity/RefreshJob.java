package com.scorpio.powerguard.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RefreshJob {

    private Long id;
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
    private LocalDateTime updateTime;
}
