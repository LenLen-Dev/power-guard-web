package com.scorpio.powerguard.util;

import com.scorpio.powerguard.enums.MailMessageType;
import com.scorpio.powerguard.enums.RoomStatusEnum;
import com.scorpio.powerguard.model.AlertMailMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertMailTemplateBuilderTest {

    private final AlertMailTemplateBuilder builder = new AlertMailTemplateBuilder();

    @Test
    void shouldBuildLowBalanceAlertHtml() {
        AlertMailMessage message = new AlertMailMessage();
        message.setMailType(MailMessageType.LOW_BALANCE_ALERT);
        message.setProject("安徽工程大学");
        message.setRoomName("男19#楼-215");
        message.setAccount("54963");
        message.setApiMessage("房间当当前剩余电量46.19");
        message.setRemain(new BigDecimal("46.19"));
        message.setStatus(RoomStatusEnum.ALERT.getCode());
        message.setFetchTime(LocalDateTime.of(2026, 4, 16, 17, 5, 0));

        String title = builder.buildSubject(message);
        String html = builder.buildHtmlContent(title, message);

        assertTrue(title.contains("低电量预警"));
        assertTrue(html.contains("#EF4444"));
        assertTrue(html.contains("54963"));
        assertTrue(html.contains("46.19 kWh"));
    }

    @Test
    void shouldBuildDailySummaryNormalHtml() {
        AlertMailMessage message = new AlertMailMessage();
        message.setMailType(MailMessageType.DAILY_SUMMARY);
        message.setProject("安徽工程大学");
        message.setRoomName("男19#楼-215");
        message.setRemain(new BigDecimal("52"));
        message.setTodayUsage(new BigDecimal("4.2"));
        message.setStatus(RoomStatusEnum.NORMAL.getCode());
        message.setFetchTime(LocalDateTime.of(2026, 4, 16, 22, 0, 0));

        String title = builder.buildSubject(message);
        String html = builder.buildHtmlContent(title, message);

        assertTrue(title.contains("当日用电摘要"));
        assertTrue(html.contains("以下为本日用电摘要"));
        assertTrue(html.contains("#3B82F6"));
        assertTrue(html.contains("4.2 kWh"));
    }

    @Test
    void shouldBuildDailySummaryWarningHtml() {
        AlertMailMessage message = new AlertMailMessage();
        message.setMailType(MailMessageType.DAILY_SUMMARY);
        message.setProject("安徽工程大学");
        message.setRoomName("男19#楼-215");
        message.setRemain(new BigDecimal("18"));
        message.setTodayUsage(new BigDecimal("6"));
        message.setStatus(RoomStatusEnum.WARNING.getCode());
        message.setFetchTime(LocalDateTime.of(2026, 4, 16, 22, 0, 0));

        String html = builder.buildHtmlContent(builder.buildSubject(message), message);

        assertTrue(html.contains("#F59E0B"));
        assertTrue(html.contains("当前电量处于阈值附近"));
    }

    @Test
    void shouldBuildDailySummaryAlertHtml() {
        AlertMailMessage message = new AlertMailMessage();
        message.setMailType(MailMessageType.DAILY_SUMMARY);
        message.setProject("安徽工程大学");
        message.setRoomName("男19#楼-215");
        message.setRemain(new BigDecimal("8"));
        message.setTodayUsage(new BigDecimal("9"));
        message.setStatus(RoomStatusEnum.ALERT.getCode());
        message.setFetchTime(LocalDateTime.of(2026, 4, 16, 22, 0, 0));

        String html = builder.buildHtmlContent(builder.buildSubject(message), message);

        assertTrue(html.contains("#EF4444"));
        assertTrue(html.contains("当前已低于阈值"));
    }

    @Test
    void shouldBuildDeferredWarningHtml() {
        AlertMailMessage message = new AlertMailMessage();
        message.setMailType(MailMessageType.DEFERRED_ALERT);
        message.setProject("安徽工程大学");
        message.setRoomName("男19#楼-215");
        message.setRemain(new BigDecimal("19"));
        message.setStatus(RoomStatusEnum.WARNING.getCode());
        message.setApiMessage("夜间静默期内曾触发低电量提醒，当前处于阈值附近，现于 07:00 补发");
        message.setFetchTime(LocalDateTime.of(2026, 4, 17, 7, 0, 0));

        String title = builder.buildSubject(message);
        String html = builder.buildHtmlContent(title, message);

        assertTrue(title.contains("夜间延迟提醒"));
        assertTrue(html.contains("07:00 延迟提醒"));
        assertTrue(html.contains("#F59E0B"));
        assertFalse(html.contains("今日耗电量"));
    }
}
