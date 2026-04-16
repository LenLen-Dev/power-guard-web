package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.constant.RedisKeys;
import com.scorpio.powerguard.entity.EmailSenderPool;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.enums.MailMessageType;
import com.scorpio.powerguard.enums.RoomStatusEnum;
import com.scorpio.powerguard.mapper.EmailSenderPoolMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.AlertMailMessage;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import com.scorpio.powerguard.properties.ExternalElectricityProperties;
import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.service.MailQueueService;
import com.scorpio.powerguard.util.AlertMailTemplateBuilder;
import com.scorpio.powerguard.util.JsonUtils;
import com.scorpio.powerguard.util.RoomStatusCalculator;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailQueueServiceImpl implements MailQueueService, ApplicationRunner {

    private static final ZoneId MAIL_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration ROOM_MAIL_STATE_TTL = Duration.ofDays(2);
    private static final LocalTime QUIET_HOURS_END = LocalTime.of(7, 0);
    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailSenderPoolMapper emailSenderPoolMapper;
    private final RoomMapper roomMapper;
    private final MailConsumerProperties mailConsumerProperties;
    private final ExternalElectricityProperties externalElectricityProperties;
    private final AlertMailTemplateBuilder alertMailTemplateBuilder;
    private final ConcurrentMap<String, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mail-queue-consumer");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void run(ApplicationArguments args) {
        if (running.compareAndSet(false, true)) {
            consumerExecutor.submit(this::consumeLoop);
            log.info("Mail queue consumer started");
        }
    }

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

    /*
     * Redis 队列消费逻辑说明：
     * 1. 这里改为普通 RPOP + 短暂休眠，而不是 BRPOP 长阻塞。
     *    原因是当前 Redis/代理环境会主动关闭阻塞连接，BRPOP 会周期性抛出 Connection closed。
     * 2. 单实例下只启动一个消费者线程，避免同一进程重复发信。
     * 3. 每个消息失败后会回推到队列尾部并累计 retryCount，超过阈值进入死信队列。
     * 4. 发件账号选择先查数据库启用号池，再用 Redis 原子计数判断是否达到每日 100 封限制。
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                String messageJson = stringRedisTemplate.opsForList()
                    .rightPop(mailConsumerProperties.getQueueKey());
                if (messageJson == null) {
                    sleepQuietly(mailConsumerProperties.getIdleWaitMillis());
                    continue;
                }
                handleMessage(messageJson);
            } catch (RedisConnectionFailureException | RedisSystemException ex) {
                log.warn("Redis queue poll failed, consumer will retry. cause={}", extractMessage(ex));
                sleepQuietly(Math.max(mailConsumerProperties.getIdleWaitMillis(), 3000L));
            } catch (Exception ex) {
                log.error("Unexpected exception in mail queue consumer loop", ex);
                sleepQuietly(Math.max(mailConsumerProperties.getIdleWaitMillis(), 1000L));
            }
        }
    }

    private void handleMessage(String messageJson) {
        AlertMailMessage message;
        try {
            message = JsonUtils.fromJson(messageJson, AlertMailMessage.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize alert mail payload={}", messageJson, ex);
            stringRedisTemplate.opsForList().leftPush(mailConsumerProperties.getDeadLetterKey(), messageJson);
            return;
        }

        try {
            EmailSenderPool sender = pickAvailableSender();
            sendMail(sender, message);
            incrementSenderDailyCount(sender.getEmailAccount());
            log.info("Mail sent successfully for room={}, target={}", message.getRoomName(), message.getTargetEmail());
        } catch (Exception ex) {
            log.error("Failed to consume alert mail payload={}", messageJson, ex);
            retryOrDeadLetter(message);
        }
    }

    private EmailSenderPool pickAvailableSender() {
        List<EmailSenderPool> senders = emailSenderPoolMapper.selectEnabledSenders();
        for (EmailSenderPool sender : senders) {
            int sentCount = getSenderDailyCount(sender.getEmailAccount());
            if (sentCount < mailConsumerProperties.getMaxDailySendCount()) {
                return sender;
            }
        }
        throw new IllegalStateException("当前没有可用的发件邮箱账号");
    }

    private void sendMail(EmailSenderPool sender, AlertMailMessage message) {
        JavaMailSenderImpl mailSender = senderCache.computeIfAbsent(sender.getEmailAccount(),
            key -> buildMailSender(sender));
        String subject = alertMailTemplateBuilder.buildSubject(message);
        String htmlContent = alertMailTemplateBuilder.buildHtmlContent(subject, message);
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(sender.getEmailAccount());
            helper.setTo(message.getTargetEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
        } catch (Exception ex) {
            throw new IllegalStateException("构建告警邮件失败", ex);
        }
        mailSender.send(mimeMessage);
    }

    private JavaMailSenderImpl buildMailSender(EmailSenderPool sender) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.qq.com");
        mailSender.setPort(587);
        mailSender.setUsername(sender.getEmailAccount());
        mailSender.setPassword(sender.getAuthCode());
        mailSender.setDefaultEncoding("UTF-8");

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return mailSender;
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
        stringRedisTemplate.opsForList().leftPush(mailConsumerProperties.getQueueKey(), JsonUtils.toJson(message));
    }

    private void retryOrDeadLetter(AlertMailMessage message) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        message.setRetryCount(retryCount + 1);
        String payload = JsonUtils.toJson(message);
        if (message.getRetryCount() > mailConsumerProperties.getMaxRetryCount()) {
            stringRedisTemplate.opsForList().leftPush(mailConsumerProperties.getDeadLetterKey(), payload);
            log.warn("Move alert mail to dead letter queue, room={}, target={}", message.getRoomName(), message.getTargetEmail());
            return;
        }
        stringRedisTemplate.opsForList().rightPush(mailConsumerProperties.getQueueKey(), payload);
    }

    private int getSenderDailyCount(String emailAccount) {
        String countValue = stringRedisTemplate.opsForValue().get(buildSenderCountKey(emailAccount));
        return countValue == null ? 0 : Integer.parseInt(countValue);
    }

    private void incrementSenderDailyCount(String emailAccount) {
        String countKey = buildSenderCountKey(emailAccount);
        Long latestCount = stringRedisTemplate.opsForValue().increment(countKey);
        stringRedisTemplate.expire(countKey, Duration.ofDays(2));
        log.debug("Increment sender count, email={}, count={}", emailAccount, latestCount);
    }

    private String buildSenderCountKey(String emailAccount) {
        return "%s:%s:%s".formatted(
            mailConsumerProperties.getSenderCountKeyPrefix(),
            LocalDate.now(MAIL_ZONE_ID).format(DATE_KEY_FORMATTER),
            emailAccount
        );
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

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        consumerExecutor.shutdownNow();
    }
}
