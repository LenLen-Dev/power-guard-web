package com.scorpio.powerguard.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.EmailSenderPool;
import com.scorpio.powerguard.mapper.EmailSenderPoolMapper;
import com.scorpio.powerguard.model.AlertMailMessage;
import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.util.AlertMailTemplateBuilder;
import com.scorpio.powerguard.util.JsonUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class MailMessageConsumerTest {

    @Mock
    private EmailSenderPoolMapper emailSenderPoolMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MailMessageConsumer consumer;
    private MailConsumerProperties properties;
    private Map<String, Integer> senderCounts;

    @BeforeEach
    void setUp() {
        properties = new MailConsumerProperties();
        properties.setQueueKey("power:mail:queue");
        properties.setRetryQueueKey("power:mail:retry");
        properties.setDeadLetterKey("power:mail:dead-letter");
        properties.setSenderCountKeyPrefix("power:mail:sender:count");
        properties.setMaxDailySendCount(100);
        properties.setMaxRetryCount(3);
        properties.setRetryDelaySeconds(10);
        properties.setListenerConcurrency(1);
        properties.setShortPauseMinSeconds(1);
        properties.setShortPauseMaxSeconds(3);
        properties.setLongPauseEveryAttempts(10);
        properties.setLongPauseMinSeconds(30);
        properties.setLongPauseMaxSeconds(60);
        properties.setSenderSwitchEveryAttempts(10);

        senderCounts = new HashMap<>();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            Integer count = senderCounts.get(key);
            return count == null ? null : String.valueOf(count);
        });
        lenient().when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            int next = senderCounts.getOrDefault(key, 0) + 1;
            senderCounts.put(key, next);
            return (long) next;
        });
        lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        consumer = spy(new MailMessageConsumer(
            emailSenderPoolMapper,
            stringRedisTemplate,
            rabbitTemplate,
            properties,
            new AlertMailTemplateBuilder()
        ));
        doNothing().when(consumer).sleepQuietly(anyLong());
    }

    @Test
    void shouldSwitchSenderAfterTenAttempts() {
        EmailSenderPool senderA = sender(1L, "a@example.com");
        EmailSenderPool senderB = sender(2L, "b@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA, senderB));
        doNothing().when(consumer).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));

        for (int i = 0; i < 11; i++) {
            consumer.consumeMessage(JsonUtils.toJson(buildMessage()));
        }

        ArgumentCaptor<EmailSenderPool> senderCaptor = ArgumentCaptor.forClass(EmailSenderPool.class);
        verify(consumer, times(11)).sendMail(senderCaptor.capture(), any(AlertMailMessage.class));
        List<String> usedSenders = senderCaptor.getAllValues().stream().map(EmailSenderPool::getEmailAccount).toList();
        assertEquals(List.of(
            "a@example.com", "a@example.com", "a@example.com", "a@example.com", "a@example.com",
            "a@example.com", "a@example.com", "a@example.com", "a@example.com", "a@example.com",
            "b@example.com"
        ), usedSenders);
    }

    @Test
    void shouldSwitchSenderWhenCurrentSenderHitsDailyLimitBeforeTenAttempts() {
        properties.setMaxDailySendCount(7);
        EmailSenderPool senderA = sender(1L, "a@example.com");
        EmailSenderPool senderB = sender(2L, "b@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA, senderB));
        doNothing().when(consumer).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));

        for (int i = 0; i < 8; i++) {
            consumer.consumeMessage(JsonUtils.toJson(buildMessage()));
        }

        ArgumentCaptor<EmailSenderPool> senderCaptor = ArgumentCaptor.forClass(EmailSenderPool.class);
        verify(consumer, times(8)).sendMail(senderCaptor.capture(), any(AlertMailMessage.class));
        List<String> usedSenders = senderCaptor.getAllValues().stream().map(EmailSenderPool::getEmailAccount).toList();
        assertEquals(List.of(
            "a@example.com", "a@example.com", "a@example.com", "a@example.com",
            "a@example.com", "a@example.com", "a@example.com", "b@example.com"
        ), usedSenders);
    }

    @Test
    void shouldRetryWhenNoSenderAvailable() {
        properties.setMaxDailySendCount(1);
        EmailSenderPool senderA = sender(1L, "a@example.com");
        EmailSenderPool senderB = sender(2L, "b@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA, senderB));
        senderCounts.put(senderCountKey("a@example.com"), 1);
        senderCounts.put(senderCountKey("b@example.com"), 1);

        consumer.consumeMessage(JsonUtils.toJson(buildMessage()));

        verify(consumer, times(0)).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("power:mail:retry"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("\"retryCount\":1"));
    }

    @Test
    void shouldMoveToDeadLetterQueueAfterMaxRetryExceeded() {
        EmailSenderPool senderA = sender(1L, "a@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA));
        doThrow(new IllegalStateException("smtp down")).when(consumer).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));

        AlertMailMessage message = buildMessage();
        message.setRetryCount(3);
        consumer.consumeMessage(JsonUtils.toJson(message));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(eq("power:mail:dead-letter"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("\"retryCount\":4"));
    }

    @Test
    void shouldUseLongPauseAfterTenGlobalAttempts() {
        EmailSenderPool senderA = sender(1L, "a@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA));
        doNothing().when(consumer).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));

        for (int i = 0; i < 10; i++) {
            consumer.consumeMessage(JsonUtils.toJson(buildMessage()));
        }

        ArgumentCaptor<Long> sleepCaptor = ArgumentCaptor.forClass(Long.class);
        verify(consumer, times(10)).sleepQuietly(sleepCaptor.capture());
        List<Long> pauses = new ArrayList<>(sleepCaptor.getAllValues());
        for (int i = 0; i < 9; i++) {
            long pause = pauses.get(i);
            assertTrue(pause >= 1000L && pause <= 3000L, "Expected short pause but got " + pause);
        }
        long longPause = pauses.get(9);
        assertTrue(longPause >= 30000L && longPause <= 60000L, "Expected long pause but got " + longPause);
    }

    @Test
    void shouldCountFailuresTowardSenderSwitching() {
        EmailSenderPool senderA = sender(1L, "a@example.com");
        EmailSenderPool senderB = sender(2L, "b@example.com");
        when(emailSenderPoolMapper.selectEnabledSenders()).thenReturn(List.of(senderA, senderB));
        doThrow(new IllegalStateException("smtp down")).when(consumer).sendMail(any(EmailSenderPool.class), any(AlertMailMessage.class));

        for (int i = 0; i < 11; i++) {
            consumer.consumeMessage(JsonUtils.toJson(buildMessage()));
        }

        ArgumentCaptor<EmailSenderPool> senderCaptor = ArgumentCaptor.forClass(EmailSenderPool.class);
        verify(consumer, times(11)).sendMail(senderCaptor.capture(), any(AlertMailMessage.class));
        List<String> usedSenders = senderCaptor.getAllValues().stream().map(EmailSenderPool::getEmailAccount).toList();
        assertEquals(List.of(
            "a@example.com", "a@example.com", "a@example.com", "a@example.com", "a@example.com",
            "a@example.com", "a@example.com", "a@example.com", "a@example.com", "a@example.com",
            "b@example.com"
        ), usedSenders);
    }

    private AlertMailMessage buildMessage() {
        AlertMailMessage message = new AlertMailMessage();
        message.setRoomId(1L);
        message.setRoomName("男19#楼-215");
        message.setBuildingName("男19#楼");
        message.setTargetEmail("target@example.com");
        message.setFetchTime(LocalDateTime.of(2026, 4, 20, 8, 0, 0));
        message.setRetryCount(0);
        return message;
    }

    private EmailSenderPool sender(Long id, String emailAccount) {
        EmailSenderPool sender = new EmailSenderPool();
        sender.setId(id);
        sender.setEmailAccount(emailAccount);
        sender.setAuthCode("auth-code");
        sender.setStatus(1);
        return sender;
    }

    private String senderCountKey(String emailAccount) {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "power:mail:sender:count:%s:%s".formatted(today, emailAccount);
    }
}
