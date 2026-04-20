package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.client.ExternalElectricityClient;
import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.util.ElectricityTotalResolver;
import com.scorpio.powerguard.util.RoomStatusCalculator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomElectricityRefreshService {

    private final RoomMapper roomMapper;
    private final ElectricityRecordMapper electricityRecordMapper;
    private final ExternalElectricityClient externalElectricityClient;
    private final MailQueueService mailQueueService;

    @Transactional(rollbackFor = Exception.class)
    public void refreshActiveRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(404, "房间不存在或已删除");
        }

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
