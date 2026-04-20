package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.model.RefreshRoomMessage;
import com.scorpio.powerguard.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshJobMessageListener {

    private final ElectricityFetchServiceImpl electricityFetchService;
    private final RoomElectricityRefreshService roomElectricityRefreshService;

    @RabbitListener(queues = "${refresh-job.queue-key}", concurrency = "${refresh-job.consumer-concurrency}")
    public void onMessage(String payload) {
        RefreshRoomMessage message = parseMessage(payload);
        if (!electricityFetchService.claimJobItem(message.getJobId(), message.getRoomId())) {
            return;
        }

        try {
            roomElectricityRefreshService.refreshActiveRoom(message.getRoomId());
            electricityFetchService.recordJobItemSuccess(message.getJobId(), message.getRoomId());
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null ? "刷新房间电量失败" : ex.getMessage();
            log.warn("Refresh room failed, jobId={}, roomId={}, message={}", message.getJobId(), message.getRoomId(), errorMessage);
            electricityFetchService.recordJobItemFailure(message.getJobId(), message.getRoomId(), errorMessage);
        }
    }

    private RefreshRoomMessage parseMessage(String payload) {
        try {
            RefreshRoomMessage message = JsonUtils.fromJson(payload, RefreshRoomMessage.class);
            if (message.getJobId() == null || message.getJobId().isBlank() || message.getRoomId() == null) {
                throw new IllegalArgumentException("refresh room message missing required fields");
            }
            return message;
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("invalid refresh room message", ex);
        }
    }
}
