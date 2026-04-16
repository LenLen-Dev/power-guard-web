package com.scorpio.powerguard.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.ElectricityRecord;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.mapper.ElectricityRecordMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.service.impl.DailySummaryMailServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySummaryMailServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private ElectricityRecordMapper electricityRecordMapper;

    @Mock
    private MailQueueService mailQueueService;

    private DailySummaryMailServiceImpl dailySummaryMailService;

    @BeforeEach
    void setUp() {
        dailySummaryMailService = new DailySummaryMailServiceImpl(roomMapper, electricityRecordMapper, mailQueueService);
    }

    @Test
    void shouldEnqueueDailySummaryForRoomsWithAlertEmail() {
        Room room = new Room();
        room.setId(1L);
        room.setBuildingName("男19#楼");
        room.setRoomName("男19#楼-215");
        room.setAlertEmail("test@example.com");
        room.setRemain(new BigDecimal("36.5"));

        ElectricityRecord start = new ElectricityRecord();
        start.setRemainSnapshot(new BigDecimal("50"));
        start.setFetchTime(LocalDateTime.now().withHour(0));

        ElectricityRecord end = new ElectricityRecord();
        end.setRemainSnapshot(new BigDecimal("36.5"));
        end.setFetchTime(LocalDateTime.now().withHour(22));

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(room));
        when(electricityRecordMapper.selectNearestSnapshot(eq(1L), any(), any(), any()))
            .thenReturn(start)
            .thenReturn(end);

        dailySummaryMailService.sendDailySummaries();

        verify(mailQueueService).enqueueDailySummary(eq(room), eq(new BigDecimal("13.5")), any(LocalDateTime.class));
    }

    @Test
    void shouldSkipRoomsWithoutAlertEmail() {
        Room room = new Room();
        room.setId(1L);
        room.setRoomName("男19#楼-215");
        room.setAlertEmail(" ");

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(room));

        dailySummaryMailService.sendDailySummaries();

        verify(mailQueueService, never()).enqueueDailySummary(any(), any(), any());
    }

    @Test
    void shouldEnqueueDailySummaryEvenWhenUsageDataMissing() {
        Room room = new Room();
        room.setId(1L);
        room.setBuildingName("男19#楼");
        room.setRoomName("男19#楼-215");
        room.setAlertEmail("test@example.com");
        room.setRemain(new BigDecimal("36.5"));

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(room));
        when(electricityRecordMapper.selectNearestSnapshot(eq(1L), any(), any(), any()))
            .thenReturn(null)
            .thenReturn(null);

        dailySummaryMailService.sendDailySummaries();

        verify(mailQueueService).enqueueDailySummary(eq(room), isNull(), any(LocalDateTime.class));
    }
}
