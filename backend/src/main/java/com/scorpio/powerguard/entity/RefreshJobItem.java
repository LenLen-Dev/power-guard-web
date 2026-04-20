package com.scorpio.powerguard.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RefreshJobItem {

    private Long id;
    private String jobId;
    private Long roomId;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
