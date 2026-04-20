package com.scorpio.powerguard.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.properties.ExternalElectricityProperties;
import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.service.impl.MailQueueServiceImpl;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MailQueueServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MailQueueServiceImpl mailQueueService;

    @BeforeEach
    void setUp() {
        MailConsumerProperties mailConsumerProperties = new MailConsumerProperties();
        mailConsumerProperties.setQueueKey("power:mail:queue");
        mailConsumerProperties.setRetryQueueKey("power:mail:retry");
        mailConsumerProperties.setDeadLetterKey("power:mail:dead-letter");
        mailConsumerProperties.setSenderCountKeyPrefix("power:mail:sender:count");
        mailConsumerProperties.setMaxDailySendCount(100);
        mailConsumerProperties.setMaxRetryCount(3);
        mailConsumerProperties.setRetryDelaySeconds(10);
        mailConsumerProperties.setListenerConcurrency(1);
        mailConsumerProperties.setShortPauseMinSeconds(1);
        mailConsumerProperties.setShortPauseMaxSeconds(3);
        mailConsumerProperties.setLongPauseEveryAttempts(10);
        mailConsumerProperties.setLongPauseMinSeconds(30);
        mailConsumerProperties.setLongPauseMaxSeconds(60);
        mailConsumerProperties.setSenderSwitchEveryAttempts(10);

        ExternalElectricityProperties externalElectricityProperties = new ExternalElectricityProperties();
        externalElectricityProperties.setArea("安徽工程大学");

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        mailQueueService = new MailQueueServiceImpl(
            stringRedisTemplate,
            rabbitTemplate,
            roomMapper,
            mailConsumerProperties,
            externalElectricityProperties
        );
    }

    @Test
    void shouldEnqueueFirstDaytimeAlert() {
        Room room = buildRoom();
        ExternalElectricityResult result = new ExternalElectricityResult();
        result.setAccount("54963");
        result.setMessage("房间当前剩余电量8");
        String alertKey = "power:mail:room:1:20260416:alert-sent";

        when(valueOperations.setIfAbsent(alertKey, "1", Duration.ofDays(2))).thenReturn(true);

        mailQueueService.enqueueLowBalanceAlert(room, result, LocalDateTime.of(2026, 4, 16, 8, 0, 0));

        verify(rabbitTemplate).convertAndSend(eq("power:mail:queue"), anyString());
        verify(valueOperations, never()).set(eq("power:mail:room:1:20260416:quiet-pending"), anyString(), any(Duration.class));
    }

    @Test
    void shouldSkipSecondDaytimeAlertWhenSlotAlreadyUsed() {
        Room room = buildRoom();
        String alertKey = "power:mail:room:1:20260416:alert-sent";

        when(valueOperations.setIfAbsent(alertKey, "1", Duration.ofDays(2))).thenReturn(false);

        mailQueueService.enqueueLowBalanceAlert(room, new ExternalElectricityResult(), LocalDateTime.of(2026, 4, 16, 8, 0, 0));

        verify(rabbitTemplate, never()).convertAndSend(eq("power:mail:queue"), anyString());
    }

    @Test
    void shouldMarkQuietPendingDuringSilentHours() {
        Room room = buildRoom();
        String alertKey = "power:mail:room:1:20260416:alert-sent";
        String quietKey = "power:mail:room:1:20260416:quiet-pending";

        when(stringRedisTemplate.hasKey(alertKey)).thenReturn(false);

        mailQueueService.enqueueLowBalanceAlert(room, new ExternalElectricityResult(), LocalDateTime.of(2026, 4, 16, 2, 0, 0));

        verify(valueOperations).set(quietKey, "1", Duration.ofDays(2));
        verify(rabbitTemplate, never()).convertAndSend(eq("power:mail:queue"), anyString());
    }

    @Test
    void shouldEnqueueDailySummaryOnlyOncePerDay() {
        Room room = buildRoom();
        String summaryKey = "power:mail:room:1:20260416:summary-sent";

        when(valueOperations.setIfAbsent(summaryKey, "1", Duration.ofDays(2))).thenReturn(true).thenReturn(false);

        mailQueueService.enqueueDailySummary(room, new BigDecimal("5"), LocalDateTime.of(2026, 4, 16, 22, 0, 0));
        mailQueueService.enqueueDailySummary(room, new BigDecimal("5"), LocalDateTime.of(2026, 4, 16, 22, 5, 0));

        verify(rabbitTemplate).convertAndSend(eq("power:mail:queue"), anyString());
    }

    @Test
    void shouldSendDeferredQuietAlertAtSevenWhenWarning() {
        Room room = buildRoom();
        room.setRemain(new BigDecimal("18"));
        room.setThreshold(new BigDecimal("10"));
        room.setStatus(1);
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String quietKey = "power:mail:room:1:%s:quiet-pending".formatted(today);
        String alertKey = "power:mail:room:1:%s:alert-sent".formatted(today);

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(room));
        when(stringRedisTemplate.hasKey(quietKey)).thenReturn(true);
        when(stringRedisTemplate.hasKey(alertKey)).thenReturn(false);
        when(valueOperations.setIfAbsent(alertKey, "1", Duration.ofDays(2))).thenReturn(true);

        mailQueueService.processDeferredQuietAlerts();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("power:mail:queue"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("\"mailType\":\"DEFERRED_ALERT\""));
        assertTrue(payloadCaptor.getValue().contains("\"status\":1"));
        verify(stringRedisTemplate).delete(quietKey);
    }

    @Test
    void shouldDiscardDeferredQuietAlertWhenRecoveredToNormal() {
        Room room = buildRoom();
        room.setRemain(new BigDecimal("35"));
        room.setThreshold(new BigDecimal("10"));
        room.setStatus(0);
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String quietKey = "power:mail:room:1:%s:quiet-pending".formatted(today);
        String alertKey = "power:mail:room:1:%s:alert-sent".formatted(today);

        when(roomMapper.selectAllActiveOrderByRoom()).thenReturn(List.of(room));
        when(stringRedisTemplate.hasKey(quietKey)).thenReturn(true);
        when(stringRedisTemplate.hasKey(alertKey)).thenReturn(false);

        mailQueueService.processDeferredQuietAlerts();

        verify(rabbitTemplate, never()).convertAndSend(eq("power:mail:queue"), anyString());
        verify(stringRedisTemplate).delete(quietKey);
    }

    private Room buildRoom() {
        Room room = new Room();
        room.setId(1L);
        room.setBuildingId("10");
        room.setBuildingName("男19#楼");
        room.setRoomName("男19#楼-215");
        room.setAlertEmail("test@example.com");
        room.setRemain(new BigDecimal("8"));
        room.setThreshold(new BigDecimal("10"));
        room.setStatus(2);
        return room;
    }
}
