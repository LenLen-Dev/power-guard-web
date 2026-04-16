package com.scorpio.powerguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.client.ExternalElectricityClient;
import com.scorpio.powerguard.dto.RoomCreateRequest;
import com.scorpio.powerguard.dto.RoomUpdateRequest;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.service.impl.RoomServiceImpl;
import com.scorpio.powerguard.vo.DailyTrendVO;
import com.scorpio.powerguard.vo.RoomStatusVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private ElectricityRecordMapper electricityRecordMapper;

    @Mock
    private ExternalElectricityClient externalElectricityClient;

    @Mock
    private MailQueueService mailQueueService;

    private RoomServiceImpl roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomServiceImpl(roomMapper, electricityRecordMapper, externalElectricityClient, mailQueueService);
    }

    @Test
    void shouldMarkRechargeWhenConsumptionIsNegative() {
        Room room = new Room();
        room.setId(1L);
        when(roomMapper.selectByIdIncludingDeleted(1L)).thenReturn(room);

        ElectricityRecord start = new ElectricityRecord();
        start.setRemainSnapshot(new BigDecimal("20"));
        start.setFetchTime(LocalDateTime.now().withHour(0));

        ElectricityRecord end = new ElectricityRecord();
        end.setRemainSnapshot(new BigDecimal("50"));
        end.setFetchTime(LocalDateTime.now().withHour(22));

        when(electricityRecordMapper.selectNearestSnapshot(eq(1L), any(), any(), any()))
            .thenReturn(start)
            .thenReturn(end);

        List<DailyTrendVO> trend = roomService.queryRoomTrend(1L, 1);
        DailyTrendVO vo = trend.get(0);

        assertEquals(BigDecimal.ZERO, vo.getConsumption());
        assertTrue(vo.getRechargeDetected());
        assertTrue(vo.getDataComplete());
    }

    @Test
    void shouldReturnIncompleteWhenSnapshotsMissing() {
        Room room = new Room();
        room.setId(1L);
        when(roomMapper.selectByIdIncludingDeleted(1L)).thenReturn(room);
        when(electricityRecordMapper.selectNearestSnapshot(eq(1L), any(), any(), any()))
            .thenReturn(null)
            .thenReturn(null);

        List<DailyTrendVO> trend = roomService.queryRoomTrend(1L, 1);
        DailyTrendVO vo = trend.get(0);

        assertNull(vo.getConsumption());
        assertFalse(vo.getDataComplete());
        assertFalse(vo.getRechargeDetected());
    }

    @Test
    void shouldInitializeRoomTotalWithInitialRemain() {
        RoomCreateRequest request = new RoomCreateRequest();
        request.setBuildingId("10");
        request.setBuildingName("男19#楼");
        request.setRoomId("215");
        request.setRoomName("男19#楼-215");
        request.setThreshold(new BigDecimal("20"));

        ExternalElectricityResult result = new ExternalElectricityResult();
        result.setRemain(new BigDecimal("46.19"));

        when(roomMapper.selectByBuildingAndRoom("10", "215", null)).thenReturn(null);
        when(externalElectricityClient.queryRoomElectricity(any(Room.class))).thenReturn(result);

        RoomStatusVO response = roomService.createRoom(request);

        assertEquals(new BigDecimal("46.19"), response.getTotal());
        assertEquals(new BigDecimal("46.19"), response.getRemain());
    }

    @Test
    void shouldListRoomsByStatusPriority() {
        Room normalRoom = buildRoom(1L, "男19#楼", "男19#楼-215", 0);
        Room alertRoom = buildRoom(2L, "男19#楼", "男19#楼-216", 2);
        Room warningRoom = buildRoom(3L, "男19#楼", "男19#楼-217", 1);

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(normalRoom, warningRoom, alertRoom));

        List<RoomStatusVO> rooms = roomService.listRoomStatus();

        assertEquals(List.of(2L, 3L, 1L), rooms.stream().map(RoomStatusVO::getId).toList());
    }

    @Test
    void shouldDeleteRoomAndAllElectricityRecords() {
        Room room = buildRoom(1L, "男19#楼", "男19#楼-215", 2);
        when(roomMapper.selectById(1L)).thenReturn(room);

        roomService.deleteRoom(1L);

        verify(electricityRecordMapper).deleteByRoomId(1L);
        verify(roomMapper).deleteById(1L);
    }

    @Test
    void shouldRejectDuplicateRoomOnCreate() {
        RoomCreateRequest request = new RoomCreateRequest();
        request.setBuildingId("10");
        request.setBuildingName("男19#楼");
        request.setRoomId("215");
        request.setRoomName("男19#楼-215");
        request.setThreshold(new BigDecimal("20"));

        when(roomMapper.selectByBuildingAndRoom("10", "215", null)).thenReturn(buildRoom(1L, "男19#楼", "男19#楼-215", 0));

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.createRoom(request));

        assertEquals("同一楼栋下房间已存在", ex.getMessage());
    }

    @Test
    void shouldRejectDuplicateRoomOnUpdate() {
        Room existing = buildRoom(1L, "男19#楼", "男19#楼-215", 0);
        Room duplicate = buildRoom(2L, "男19#楼", "男19#楼-216", 0);
        RoomUpdateRequest request = new RoomUpdateRequest();
        request.setBuildingId("10");
        request.setBuildingName("男19#楼");
        request.setRoomId("216");
        request.setRoomName("男19#楼-216");
        request.setThreshold(new BigDecimal("20"));

        when(roomMapper.selectById(1L)).thenReturn(existing);
        when(roomMapper.selectByBuildingAndRoom("10", "216", 1L)).thenReturn(duplicate);

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.updateRoom(1L, request));

        assertEquals("同一楼栋下房间已存在", ex.getMessage());
    }

    @Test
    void shouldConvertDuplicateKeyExceptionOnCreate() {
        RoomCreateRequest request = new RoomCreateRequest();
        request.setBuildingId("10");
        request.setBuildingName("男19#楼");
        request.setRoomId("215");
        request.setRoomName("男19#楼-215");
        request.setThreshold(new BigDecimal("20"));

        ExternalElectricityResult result = new ExternalElectricityResult();
        result.setRemain(new BigDecimal("46.19"));

        when(roomMapper.selectByBuildingAndRoom("10", "215", null)).thenReturn(null);
        when(externalElectricityClient.queryRoomElectricity(any(Room.class))).thenReturn(result);
        doThrow(new DuplicateKeyException("duplicate")).when(roomMapper).insert(any(Room.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.createRoom(request));

        assertEquals("同一楼栋下房间已存在", ex.getMessage());
    }

    @Test
    void shouldConvertDuplicateKeyExceptionOnUpdate() {
        Room existing = buildRoom(1L, "男19#楼", "男19#楼-215", 0);
        RoomUpdateRequest request = new RoomUpdateRequest();
        request.setBuildingId("10");
        request.setBuildingName("男19#楼");
        request.setRoomId("215");
        request.setRoomName("男19#楼-215");
        request.setThreshold(new BigDecimal("20"));

        when(roomMapper.selectById(1L)).thenReturn(existing);
        when(roomMapper.selectByBuildingAndRoom("10", "215", 1L)).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(roomMapper).updateBasicInfo(any(Room.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.updateRoom(1L, request));

        assertEquals("同一楼栋下房间已存在", ex.getMessage());
    }

    private Room buildRoom(Long id, String buildingName, String roomName, Integer status) {
        Room room = new Room();
        room.setId(id);
        room.setBuildingId("10");
        room.setBuildingName(buildingName);
        room.setRoomId(roomName);
        room.setRoomName(roomName);
        room.setThreshold(new BigDecimal("10"));
        room.setRemain(new BigDecimal("20"));
        room.setStatus(status);
        return room;
    }
}
