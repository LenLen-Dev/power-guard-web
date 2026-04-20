package com.scorpio.powerguard.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RefreshRoomMessage {

    private String jobId;
    private Long roomId;

    public RefreshRoomMessage(String jobId, Long roomId) {
        this.jobId = jobId;
        this.roomId = roomId;
    }
}
