package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.service.DailySummaryMailService;
import com.scorpio.powerguard.service.MailQueueService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryMailServiceImpl implements DailySummaryMailService {

    private static final ZoneId MAIL_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RoomMapper roomMapper;
    private final ElectricityRecordMapper electricityRecordMapper;
    private final MailQueueService mailQueueService;

    @Override
    public void sendDailySummaries() {
        List<Room> rooms = roomMapper.selectAllActiveOrderByRoom();
        LocalDateTime sendTime = LocalDateTime.now(MAIL_ZONE_ID);
        LocalDate today = sendTime.toLocalDate();

        log.info("Start daily summary mail task for {} rooms", rooms.size());
        for (Room room : rooms) {
            if (room.getAlertEmail() == null || room.getAlertEmail().isBlank()) {
                continue;
            }
            try {
                BigDecimal todayUsage = resolveTodayUsage(room.getId(), today);
                mailQueueService.enqueueDailySummary(room, todayUsage, sendTime);
            } catch (Exception ex) {
                log.error("Failed to enqueue daily summary mail for roomId={}, roomName={}, buildingId={}, buildingName={}",
                    room.getId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(), ex);
            }
        }
        log.info("Finish daily summary mail task for {} rooms", rooms.size());
    }

    private BigDecimal resolveTodayUsage(Long roomId, LocalDate date) {
        ElectricityRecord startRecord = electricityRecordMapper.selectNearestSnapshot(
            roomId,
            date.atTime(0, 0),
            date.atTime(1, 0),
            date.atStartOfDay()
        );
        ElectricityRecord endRecord = electricityRecordMapper.selectNearestSnapshot(
            roomId,
            date.atTime(21, 0),
            date.atTime(23, 0),
            date.atTime(22, 0)
        );
        if (startRecord == null || endRecord == null) {
            return null;
        }

        BigDecimal usage = startRecord.getRemainSnapshot().subtract(endRecord.getRemainSnapshot());
        return usage.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : usage;
    }
}
