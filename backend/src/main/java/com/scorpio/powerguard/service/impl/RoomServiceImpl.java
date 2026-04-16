package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.client.ExternalElectricityClient;
import com.scorpio.powerguard.dto.RoomCreateRequest;
import com.scorpio.powerguard.dto.RoomUpdateRequest;
import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.enums.RoomStatusEnum;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.service.RoomService;
import com.scorpio.powerguard.util.RoomStatusCalculator;
import com.scorpio.powerguard.vo.DailyTrendVO;
import com.scorpio.powerguard.vo.RoomStatusVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomMapper roomMapper;
    private final ElectricityRecordMapper electricityRecordMapper;
    private final ExternalElectricityClient externalElectricityClient;
    private final MailQueueService mailQueueService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomStatusVO createRoom(RoomCreateRequest request) {
        validateDuplicate(request.getBuildingId(), request.getRoomId(), null);
        Room room = new Room();
        room.setBuildingId(request.getBuildingId());
        room.setBuildingName(request.getBuildingName());
        room.setRoomId(request.getRoomId());
        room.setRoomName(request.getRoomName());
        room.setAlertEmail(request.getAlertEmail());
        room.setThreshold(request.getThreshold());
        ExternalElectricityResult result = externalElectricityClient.queryRoomElectricity(room);
        LocalDateTime now = LocalDateTime.now();
        room.setTotal(result.getRemain());
        room.setRemain(result.getRemain());
        room.setStatus(RoomStatusCalculator.calculate(result.getRemain(), request.getThreshold()));
        room.setDeleted(0);
        room.setUpdateTime(now);
        insertRoom(room);

        ElectricityRecord record = new ElectricityRecord();
        record.setRoomId(room.getId());
        record.setRemainSnapshot(result.getRemain());
        record.setFetchTime(now);
        electricityRecordMapper.insert(record);

        if (room.getStatus() != null && room.getStatus() == 2
            && room.getAlertEmail() != null && !room.getAlertEmail().isBlank()) {
            mailQueueService.enqueueLowBalanceAlert(room, result, now);
        }

        return toStatusVO(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomStatusVO updateRoom(Long id, RoomUpdateRequest request) {
        Room existing = roomMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("房间不存在");
        }
        validateDuplicate(request.getBuildingId(), request.getRoomId(), id);
        existing.setBuildingId(request.getBuildingId());
        existing.setBuildingName(request.getBuildingName());
        existing.setRoomId(request.getRoomId());
        existing.setRoomName(request.getRoomName());
        existing.setAlertEmail(request.getAlertEmail());
        existing.setThreshold(request.getThreshold());
        existing.setStatus(RoomStatusCalculator.calculate(existing.getRemain(), request.getThreshold()));
        existing.setUpdateTime(LocalDateTime.now());
        updateRoomBasicInfo(existing);
        return toStatusVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoom(Long id) {
        Room existing = roomMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("房间不存在或已删除");
        }
        electricityRecordMapper.deleteByRoomId(id);
        roomMapper.deleteById(id);
    }

    @Override
    public List<RoomStatusVO> listRoomStatus() {
        return roomMapper.selectAllActiveOrderByRoom()
            .stream()
            .sorted(roomStatusComparator())
            .map(this::toStatusVO)
            .toList();
    }

    @Override
    public List<DailyTrendVO> queryRoomTrend(Long roomId, Integer days) {
        int queryDays = (days == null || days <= 0) ? 7 : Math.min(days, 30);
        Room room = roomMapper.selectByIdIncludingDeleted(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        LocalDate startDate = LocalDate.now().minusDays(queryDays - 1L);
        List<DailyTrendVO> trendList = new ArrayList<>();
        for (int i = 0; i < queryDays; i++) {
            LocalDate date = startDate.plusDays(i);
            trendList.add(buildDailyTrend(roomId, date));
        }
        return trendList;
    }

    private DailyTrendVO buildDailyTrend(Long roomId, LocalDate date) {
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

        DailyTrendVO vo = new DailyTrendVO();
        vo.setDate(date);
        vo.setStartRemain(startRecord == null ? null : startRecord.getRemainSnapshot());
        vo.setEndRemain(endRecord == null ? null : endRecord.getRemainSnapshot());
        boolean complete = startRecord != null && endRecord != null;
        vo.setDataComplete(complete);
        vo.setRechargeDetected(false);
        if (!complete) {
            vo.setConsumption(null);
            return vo;
        }

        BigDecimal consumption = startRecord.getRemainSnapshot().subtract(endRecord.getRemainSnapshot());
        if (consumption.compareTo(BigDecimal.ZERO) < 0) {
            vo.setConsumption(BigDecimal.ZERO);
            vo.setRechargeDetected(true);
        } else {
            vo.setConsumption(consumption);
        }
        return vo;
    }

    private void validateDuplicate(String buildingId, String roomId, Long excludeId) {
        Room duplicate = roomMapper.selectByBuildingAndRoom(buildingId, roomId, excludeId);
        if (duplicate != null) {
            throw new BusinessException("同一楼栋下房间已存在");
        }
    }

    private void insertRoom(Room room) {
        try {
            roomMapper.insert(room);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("同一楼栋下房间已存在");
        }
    }

    private void updateRoomBasicInfo(Room room) {
        try {
            roomMapper.updateBasicInfo(room);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("同一楼栋下房间已存在");
        }
    }

    private Comparator<Room> roomStatusComparator() {
        return Comparator.comparingInt(this::statusPriority)
            .reversed()
            .thenComparing(room -> defaultString(room.getBuildingName()))
            .thenComparing(room -> defaultString(room.getRoomName()));
    }

    private int statusPriority(Room room) {
        return room == null || room.getStatus() == null ? -1 : room.getStatus();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private RoomStatusVO toStatusVO(Room room) {
        RoomStatusVO vo = new RoomStatusVO();
        vo.setId(room.getId());
        vo.setBuildingId(room.getBuildingId());
        vo.setBuildingName(room.getBuildingName());
        vo.setRoomId(room.getRoomId());
        vo.setRoomName(room.getRoomName());
        vo.setAlertEmail(room.getAlertEmail());
        vo.setThreshold(room.getThreshold());
        vo.setTotal(room.getTotal());
        vo.setRemain(room.getRemain());
        vo.setStatus(room.getStatus());
        vo.setStatusDesc(RoomStatusEnum.getDescByCode(room.getStatus()));
        vo.setLowThreshold(room.getRemain() != null && room.getThreshold() != null
            && room.getRemain().compareTo(room.getThreshold()) <= 0);
        vo.setUpdateTime(room.getUpdateTime());
        return vo;
    }
}
