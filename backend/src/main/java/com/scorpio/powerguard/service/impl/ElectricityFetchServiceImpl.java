package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.client.ExternalElectricityClient;
import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.service.ElectricityFetchService;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.util.ElectricityTotalResolver;
import com.scorpio.powerguard.util.RoomStatusCalculator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectricityFetchServiceImpl implements ElectricityFetchService {

    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    private final RoomMapper roomMapper;
    private final ElectricityRecordMapper electricityRecordMapper;
    private final ExternalElectricityClient externalElectricityClient;
    private final MailQueueService mailQueueService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void fetchAllActiveRooms() {
        if (!refreshRunning.compareAndSet(false, true)) {
            log.info("Skip scheduled electricity fetch because another refresh task is already running");
            return;
        }
        try {
            doFetchAllActiveRooms("scheduled");
        } finally {
            refreshRunning.set(false);
        }
    }

    @Override
    public void manualRefreshAllActiveRooms() {
        if (!refreshRunning.compareAndSet(false, true)) {
            throw new BusinessException(429, "刷新任务执行中，请稍后重试");
        }
        try {
            doFetchAllActiveRooms("manual");
        } finally {
            refreshRunning.set(false);
        }
    }

    private void doFetchAllActiveRooms(String triggerSource) {
        List<Room> rooms = roomMapper.selectAllActive();
        log.info("Start {} electricity fetch for {} rooms", triggerSource, rooms.size());
        for (Room room : rooms) {
            try {
                transactionTemplate.executeWithoutResult(status -> refreshRoom(room));
            } catch (Exception ex) {
                log.error("Failed to refresh electricity for roomId={}, externalRoomId={}, roomName={}, buildingId={}, buildingName={}",
                    room.getId(), room.getRoomId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(), ex);
            }
        }
        log.info("Finish {} electricity fetch for {} rooms", triggerSource, rooms.size());
    }

    private void refreshRoom(Room room) {
        ExternalElectricityResult result = externalElectricityClient.queryRoomElectricity(room);
        LocalDateTime now = LocalDateTime.now();
        Integer status = RoomStatusCalculator.calculate(result.getRemain(), room.getThreshold());
        var resolvedTotal = ElectricityTotalResolver.resolve(result.getRemain(), room.getRemain(), room.getTotal());

        if (room.getRemain() != null && result.getRemain() != null && result.getRemain().compareTo(room.getRemain()) > 0) {
            log.info("Detected remain increase for roomId={}, externalRoomId={}, previousRemain={}, latestRemain={}, reset total to latest remain",
                room.getId(), room.getRoomId(), room.getRemain(), result.getRemain());
        }

        roomMapper.updateElectricityData(room.getId(), resolvedTotal, result.getRemain(), status, now);

        ElectricityRecord record = new ElectricityRecord();
        record.setRoomId(room.getId());
        record.setRemainSnapshot(result.getRemain());
        record.setFetchTime(now);
        electricityRecordMapper.insert(record);

        room.setTotal(resolvedTotal);
        room.setRemain(result.getRemain());
        room.setStatus(status);
        room.setUpdateTime(now);

        if (status != null && status == 2 && room.getAlertEmail() != null && !room.getAlertEmail().isBlank()) {
            mailQueueService.enqueueLowBalanceAlert(room, result, now);
        }
    }
}
