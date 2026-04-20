package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.constant.RedisKeys;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.enums.MailMessageType;
import com.scorpio.powerguard.enums.RoomStatusEnum;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.AlertMailMessage;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.properties.ExternalElectricityProperties;
import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.util.JsonUtils;
import com.scorpio.powerguard.util.RoomStatusCalculator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailQueueServiceImpl implements MailQueueService {

    private static final ZoneId MAIL_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration ROOM_MAIL_STATE_TTL = Duration.ofDays(2);
    private static final LocalTime QUIET_HOURS_END = LocalTime.of(7, 0);
    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RoomMapper roomMapper;
    private final MailConsumerProperties mailConsumerProperties;
    private final ExternalElectricityProperties externalElectricityProperties;

    @Override
    public void enqueueLowBalanceAlert(Room room, ExternalElectricityResult result, LocalDateTime fetchTime) {
        if (!hasAlertEmail(room)) {
            return;
        }
        LocalDateTime businessTime = normalizeBusinessTime(fetchTime);
        LocalDate businessDate = businessTime.toLocalDate();

        if (isQuietHours(businessTime)) {
            if (hasRoomMailState(buildAlertSentKey(room.getId(), businessDate))) {
                log.info("Skip quiet-hour alert because alert slot already used, roomId={}, roomName={}",
                    room.getId(), room.getRoomName());
                return;
            }
            markQuietPending(room.getId(), businessDate);
            log.info("Defer quiet-hour alert for roomId={}, roomName={}, until 07:00",
                room.getId(), room.getRoomName());
            return;
        }

        if (!tryOccupyAlertSlot(room.getId(), businessDate)) {
            log.info("Skip alert because daily alert slot already used, roomId={}, roomName={}",
                room.getId(), room.getRoomName());
            return;
        }

        AlertMailMessage message = buildBaseMessage(room, businessTime, MailMessageType.LOW_BALANCE_ALERT);
        message.setAccount(result == null ? null : result.getAccount());
        message.setApiMessage(result == null ? null : result.getMessage());
        pushMessage(message);
        log.info("Enqueued low balance alert for room={}, email={}", room.getRoomName(), room.getAlertEmail());
    }

    @Override
    public void enqueueDailySummary(Room room, BigDecimal todayUsage, LocalDateTime fetchTime) {
        if (!hasAlertEmail(room)) {
            return;
        }
        LocalDateTime businessTime = normalizeBusinessTime(fetchTime);
        LocalDate businessDate = businessTime.toLocalDate();

        if (!tryOccupySummarySlot(room.getId(), businessDate)) {
            log.info("Skip daily summary because summary slot already used, roomId={}, roomName={}",
                room.getId(), room.getRoomName());
            return;
        }

        AlertMailMessage message = buildBaseMessage(room, businessTime, MailMessageType.DAILY_SUMMARY);
        message.setTodayUsage(todayUsage);
        message.setApiMessage(todayUsage == null
            ? "今日耗电量数据不足，已展示当前剩余电量"
            : "本日耗电量按 00:00 至 22:00 快照估算");
        pushMessage(message);
        log.info("Enqueued daily summary mail for room={}, email={}", room.getRoomName(), room.getAlertEmail());
    }

    @Override
    public void processDeferredQuietAlerts() {
        LocalDateTime businessTime = LocalDateTime.now(MAIL_ZONE_ID);
        LocalDate businessDate = businessTime.toLocalDate();
        List<Room> rooms = roomMapper.selectAllActiveOrderByRoom();

        log.info("Start deferred quiet-hour alert task for {} rooms", rooms.size());
        for (Room room : rooms) {
            String quietPendingKey = buildQuietPendingKey(room.getId(), businessDate);
            if (!hasRoomMailState(quietPendingKey)) {
                continue;
            }

            try {
                if (!hasAlertEmail(room)) {
                    clearQuietPending(room.getId(), businessDate);
                    continue;
                }

                if (hasRoomMailState(buildAlertSentKey(room.getId(), businessDate))) {
                    clearQuietPending(room.getId(), businessDate);
                    continue;
                }

                Integer currentStatus = RoomStatusCalculator.calculate(room.getRemain(), room.getThreshold());
                if (currentStatus == null || currentStatus == RoomStatusEnum.NORMAL.getCode()) {
                    clearQuietPending(room.getId(), businessDate);
                    continue;
                }

                if (!tryOccupyAlertSlot(room.getId(), businessDate)) {
                    clearQuietPending(room.getId(), businessDate);
                    continue;
                }

                AlertMailMessage message = buildBaseMessage(room, businessTime, MailMessageType.DEFERRED_ALERT);
                message.setStatus(currentStatus);
                message.setApiMessage(buildDeferredAlertMessage(currentStatus));
                pushMessage(message);
                clearQuietPending(room.getId(), businessDate);
                log.info("Enqueued deferred quiet-hour alert for room={}, email={}", room.getRoomName(), room.getAlertEmail());
            } catch (Exception ex) {
                log.error("Failed to process deferred quiet-hour alert for roomId={}, roomName={}, buildingId={}, buildingName={}",
                    room.getId(), room.getRoomName(), room.getBuildingId(), room.getBuildingName(), ex);
            }
        }
        log.info("Finish deferred quiet-hour alert task for {} rooms", rooms.size());
    }

    @Override
    public void clearDailySendCounters() {
        String pattern = mailConsumerProperties.getSenderCountKeyPrefix() + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Long deletedCount = stringRedisTemplate.delete(keys);
        log.info("Cleared {} daily mail sender counter keys", deletedCount);
    }

    private AlertMailMessage buildBaseMessage(Room room, LocalDateTime fetchTime, MailMessageType mailType) {
        AlertMailMessage message = new AlertMailMessage();
        message.setRoomId(room.getId());
        message.setMailType(mailType);
        message.setProject(externalElectricityProperties.getArea());
        message.setRoomName(room.getRoomName());
        message.setBuildingName(room.getBuildingName());
        message.setTargetEmail(room.getAlertEmail());
        message.setRemain(room.getRemain());
        message.setThreshold(room.getThreshold());
        message.setStatus(room.getStatus());
        message.setFetchTime(fetchTime);
        message.setRetryCount(0);
        return message;
    }

    private void pushMessage(AlertMailMessage message) {
        rabbitTemplate.convertAndSend(mailConsumerProperties.getQueueKey(), JsonUtils.toJson(message));
    }

    private boolean hasAlertEmail(Room room) {
        return room != null && room.getAlertEmail() != null && !room.getAlertEmail().isBlank();
    }

    private LocalDateTime normalizeBusinessTime(LocalDateTime fetchTime) {
        return fetchTime == null ? LocalDateTime.now(MAIL_ZONE_ID) : fetchTime;
    }

    private boolean isQuietHours(LocalDateTime businessTime) {
        return businessTime.toLocalTime().isBefore(QUIET_HOURS_END);
    }

    private boolean tryOccupyAlertSlot(Long roomId, LocalDate businessDate) {
        return tryOccupyRoomMailState(buildAlertSentKey(roomId, businessDate));
    }

    private boolean tryOccupySummarySlot(Long roomId, LocalDate businessDate) {
        return tryOccupyRoomMailState(buildSummarySentKey(roomId, businessDate));
    }

    private boolean tryOccupyRoomMailState(String key) {
        Boolean occupied = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ROOM_MAIL_STATE_TTL);
        return Boolean.TRUE.equals(occupied);
    }

    private boolean hasRoomMailState(String key) {
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    private void markQuietPending(Long roomId, LocalDate businessDate) {
        stringRedisTemplate.opsForValue().set(buildQuietPendingKey(roomId, businessDate), "1", ROOM_MAIL_STATE_TTL);
    }

    private void clearQuietPending(Long roomId, LocalDate businessDate) {
        stringRedisTemplate.delete(buildQuietPendingKey(roomId, businessDate));
    }

    private String buildAlertSentKey(Long roomId, LocalDate businessDate) {
        return buildRoomMailStateKey(roomId, businessDate, RedisKeys.ALERT_SENT_SUFFIX);
    }

    private String buildSummarySentKey(Long roomId, LocalDate businessDate) {
        return buildRoomMailStateKey(roomId, businessDate, RedisKeys.SUMMARY_SENT_SUFFIX);
    }

    private String buildQuietPendingKey(Long roomId, LocalDate businessDate) {
        return buildRoomMailStateKey(roomId, businessDate, RedisKeys.QUIET_PENDING_SUFFIX);
    }

    private String buildRoomMailStateKey(Long roomId, LocalDate businessDate, String suffix) {
        return "%s:%s:%s:%s".formatted(
            RedisKeys.ROOM_MAIL_STATE_PREFIX,
            roomId,
            businessDate.format(DATE_KEY_FORMATTER),
            suffix
        );
    }

    private String buildDeferredAlertMessage(Integer currentStatus) {
        if (currentStatus != null && currentStatus == RoomStatusEnum.ALERT.getCode()) {
            return "夜间静默期内曾触发低电量提醒，当前仍低于阈值，现于 07:00 补发";
        }
        return "夜间静默期内曾触发低电量提醒，当前处于阈值附近，现于 07:00 补发";
    }
}
