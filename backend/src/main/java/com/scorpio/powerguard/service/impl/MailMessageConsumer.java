package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.entity.EmailSenderPool;
import com.scorpio.powerguard.model.AlertMailMessage;
import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.mapper.EmailSenderPoolMapper;
import com.scorpio.powerguard.util.AlertMailTemplateBuilder;
import com.scorpio.powerguard.util.JsonUtils;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailMessageConsumer {

    private final EmailSenderPoolMapper emailSenderPoolMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final MailConsumerProperties mailConsumerProperties;
    private final AlertMailTemplateBuilder alertMailTemplateBuilder;
    private final ConcurrentMap<String, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();
    private final Object dispatchStateLock = new Object();

    private String currentSenderEmailAccount;
    private int currentSenderAttemptCount;
    private int globalAttemptCount;

    @RabbitListener(queues = "${mail-consumer.queue-key}", concurrency = "${mail-consumer.listener-concurrency}")
    public void consumeMessage(String payload) {
        AlertMailMessage message = parseMessage(payload);
        EmailSenderPool sender = null;
        try {
            sender = pickAvailableSender();
            sendMail(sender, message);
            incrementSenderDailyCount(sender.getEmailAccount());
            log.info("Mail sent successfully for room={}, target={}, sender={}",
                message.getRoomName(), message.getTargetEmail(), sender.getEmailAccount());
        } catch (Exception ex) {
            log.error("Failed to consume alert mail payload={}", payload, ex);
            handleConsumeFailure(message, sender, ex);
        } finally {
            pauseAfterAttempt(sender);
        }
    }

    private AlertMailMessage parseMessage(String payload) {
        try {
            return JsonUtils.fromJson(payload, AlertMailMessage.class);
        } catch (Exception ex) {
            log.error("Invalid alert mail payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("invalid alert mail payload", ex);
        }
    }

    private EmailSenderPool pickAvailableSender() {
        synchronized (dispatchStateLock) {
            List<EmailSenderPool> senders = emailSenderPoolMapper.selectEnabledSenders();
            if (senders == null || senders.isEmpty()) {
                currentSenderEmailAccount = null;
                currentSenderAttemptCount = 0;
                throw new IllegalStateException("当前没有可用的发件邮箱账号");
            }

            List<EmailSenderPool> availableSenders = senders.stream()
                .filter(this::isSenderAvailable)
                .toList();
            if (availableSenders.isEmpty()) {
                currentSenderEmailAccount = null;
                currentSenderAttemptCount = 0;
                throw new IllegalStateException("当前没有可用的发件邮箱账号");
            }

            EmailSenderPool currentSender = findSenderByEmail(availableSenders, currentSenderEmailAccount);
            if (currentSender != null && currentSenderAttemptCount < getSenderSwitchEveryAttempts()) {
                return currentSender;
            }

            EmailSenderPool nextSender = selectNextAvailableSender(senders, availableSenders);
            currentSenderEmailAccount = nextSender.getEmailAccount();
            currentSenderAttemptCount = 0;
            return nextSender;
        }
    }

    private EmailSenderPool selectNextAvailableSender(List<EmailSenderPool> allSenders, List<EmailSenderPool> availableSenders) {
        int currentIndex = indexOfSender(allSenders, currentSenderEmailAccount);
        for (int offset = 1; offset <= allSenders.size(); offset++) {
            int candidateIndex = currentIndex < 0 ? offset - 1 : (currentIndex + offset) % allSenders.size();
            EmailSenderPool candidate = allSenders.get(candidateIndex);
            if (findSenderByEmail(availableSenders, candidate.getEmailAccount()) != null) {
                return candidate;
            }
        }
        throw new IllegalStateException("当前没有可用的发件邮箱账号");
    }

    private int indexOfSender(List<EmailSenderPool> senders, String emailAccount) {
        if (emailAccount == null || emailAccount.isBlank()) {
            return -1;
        }
        for (int i = 0; i < senders.size(); i++) {
            if (emailAccount.equals(senders.get(i).getEmailAccount())) {
                return i;
            }
        }
        return -1;
    }

    private EmailSenderPool findSenderByEmail(List<EmailSenderPool> senders, String emailAccount) {
        if (emailAccount == null || emailAccount.isBlank()) {
            return null;
        }
        return senders.stream()
            .filter(sender -> emailAccount.equals(sender.getEmailAccount()))
            .findFirst()
            .orElse(null);
    }

    private boolean isSenderAvailable(EmailSenderPool sender) {
        return sender != null && sender.getEmailAccount() != null
            && getSenderDailyCount(sender.getEmailAccount()) < getMaxDailySendCount()
            && !isSenderCoolingDown(sender.getEmailAccount());
    }

    void sendMail(EmailSenderPool sender, AlertMailMessage message) {
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

    private void handleConsumeFailure(AlertMailMessage message, EmailSenderPool sender, Exception ex) {
        if (isNoAvailableSenderException(ex)) {
            republishForRetryOrDeadLetter(message, ex, mailConsumerProperties.getAuthRetryQueueKey(), "auth-retry");
            return;
        }
        if (isAuthenticationFailure(ex)) {
            if (sender != null && sender.getEmailAccount() != null) {
                markSenderCooldown(sender.getEmailAccount(), ex);
            }
            republishForRetryOrDeadLetter(message, ex, mailConsumerProperties.getAuthRetryQueueKey(), "auth-retry");
            return;
        }
        if (isMailBuildFailure(ex)) {
            republishToDeadLetter(message, ex, "invalid-message");
            return;
        }
        republishForRetryOrDeadLetter(message, ex, mailConsumerProperties.getRetryQueueKey(), "retry");
    }

    private void republishForRetryOrDeadLetter(AlertMailMessage message, Exception ex, String retryQueueKey, String retryType) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        message.setRetryCount(retryCount + 1);
        String payload = JsonUtils.toJson(message);
        String queueKey = message.getRetryCount() > getMaxRetryCount()
            ? mailConsumerProperties.getDeadLetterKey()
            : retryQueueKey;
        try {
            rabbitTemplate.convertAndSend(queueKey, payload);
            if (queueKey.equals(mailConsumerProperties.getDeadLetterKey())) {
                log.warn("Move alert mail to dead letter queue, room={}, target={}, retryType={}, retryCount={}, reason={}",
                    message.getRoomName(), message.getTargetEmail(), retryType, message.getRetryCount(), ex.getMessage());
            } else {
                log.warn("Republish alert mail to {} queue, room={}, target={}, retryCount={}, reason={}",
                    retryType, message.getRoomName(), message.getTargetEmail(), message.getRetryCount(), ex.getMessage());
            }
        } catch (Exception publishEx) {
            throw new AmqpRejectAndDontRequeueException("failed to republish alert mail message", publishEx);
        }
    }

    private void republishToDeadLetter(AlertMailMessage message, Exception ex, String reasonType) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        message.setRetryCount(retryCount + 1);
        String payload = JsonUtils.toJson(message);
        try {
            rabbitTemplate.convertAndSend(mailConsumerProperties.getDeadLetterKey(), payload);
            log.warn("Move alert mail to dead letter queue, room={}, target={}, reasonType={}, reason={}",
                message.getRoomName(), message.getTargetEmail(), reasonType, ex.getMessage());
        } catch (Exception publishEx) {
            throw new AmqpRejectAndDontRequeueException("failed to republish alert mail message to dead letter", publishEx);
        }
    }

    private void pauseAfterAttempt(EmailSenderPool sender) {
        long pauseMillis;
        synchronized (dispatchStateLock) {
            globalAttemptCount++;
            if (sender != null) {
                if (currentSenderEmailAccount == null || !currentSenderEmailAccount.equals(sender.getEmailAccount())) {
                    currentSenderEmailAccount = sender.getEmailAccount();
                    currentSenderAttemptCount = 0;
                }
                currentSenderAttemptCount++;
            }

            if (globalAttemptCount >= getLongPauseEveryAttempts()) {
                pauseMillis = nextPauseMillis(getLongPauseMinSeconds(), getLongPauseMaxSeconds());
                globalAttemptCount = 0;
            } else {
                pauseMillis = nextPauseMillis(getShortPauseMinSeconds(), getShortPauseMaxSeconds());
            }
        }
        sleepQuietly(pauseMillis);
    }

    long nextPauseMillis(int minSeconds, int maxSeconds) {
        int effectiveMin = Math.max(0, minSeconds);
        int effectiveMax = Math.max(effectiveMin, maxSeconds);
        long seconds = ThreadLocalRandom.current().nextLong(effectiveMin, effectiveMax + 1L);
        return seconds * 1000L;
    }

    void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
            java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
            emailAccount
        );
    }

    private boolean isSenderCoolingDown(String emailAccount) {
        Boolean hasKey = stringRedisTemplate.hasKey(buildSenderCooldownKey(emailAccount));
        return Boolean.TRUE.equals(hasKey);
    }

    private void markSenderCooldown(String emailAccount, Exception ex) {
        String cooldownKey = buildSenderCooldownKey(emailAccount);
        stringRedisTemplate.opsForValue().set(cooldownKey, sanitizeReason(ex), Duration.ofSeconds(getSenderCooldownSeconds()));
        senderCache.remove(emailAccount);
        synchronized (dispatchStateLock) {
            if (emailAccount.equals(currentSenderEmailAccount)) {
                currentSenderEmailAccount = null;
                currentSenderAttemptCount = 0;
            }
        }
        log.warn("Cooldown mail sender for {} seconds, email={}, reason={}",
            getSenderCooldownSeconds(), emailAccount, ex.getMessage());
    }

    private String buildSenderCooldownKey(String emailAccount) {
        return "%s:%s".formatted(getSenderCooldownKeyPrefix(), emailAccount);
    }

    private String sanitizeReason(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        if (message == null || message.isBlank()) {
            return "mail sender cooldown";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private boolean isNoAvailableSenderException(Exception ex) {
        return ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().contains("当前没有可用的发件邮箱账号");
    }

    private boolean isAuthenticationFailure(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MailAuthenticationException || current instanceof AuthenticationFailedException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("535 Login fail") || message.contains("login frequency limited"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMailBuildFailure(Exception ex) {
        return ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().contains("构建告警邮件失败");
    }

    private int getMaxRetryCount() {
        return mailConsumerProperties.getMaxRetryCount() == null ? 3 : mailConsumerProperties.getMaxRetryCount();
    }

    private String getSenderCooldownKeyPrefix() {
        return mailConsumerProperties.getSenderCooldownKeyPrefix() == null
            ? "power:mail:sender:cooldown"
            : mailConsumerProperties.getSenderCooldownKeyPrefix();
    }

    private int getMaxDailySendCount() {
        return mailConsumerProperties.getMaxDailySendCount() == null ? 100 : mailConsumerProperties.getMaxDailySendCount();
    }

    private int getShortPauseMinSeconds() {
        return mailConsumerProperties.getShortPauseMinSeconds() == null ? 10 : mailConsumerProperties.getShortPauseMinSeconds();
    }

    private int getShortPauseMaxSeconds() {
        return mailConsumerProperties.getShortPauseMaxSeconds() == null ? 30 : mailConsumerProperties.getShortPauseMaxSeconds();
    }

    private int getLongPauseEveryAttempts() {
        return mailConsumerProperties.getLongPauseEveryAttempts() == null ? 10 : mailConsumerProperties.getLongPauseEveryAttempts();
    }

    private int getLongPauseMinSeconds() {
        return mailConsumerProperties.getLongPauseMinSeconds() == null ? 600 : mailConsumerProperties.getLongPauseMinSeconds();
    }

    private int getLongPauseMaxSeconds() {
        return mailConsumerProperties.getLongPauseMaxSeconds() == null ? 1800 : mailConsumerProperties.getLongPauseMaxSeconds();
    }

    private int getSenderSwitchEveryAttempts() {
        return mailConsumerProperties.getSenderSwitchEveryAttempts() == null ? 10 : mailConsumerProperties.getSenderSwitchEveryAttempts();
    }

    private int getSenderCooldownSeconds() {
        return mailConsumerProperties.getSenderCooldownSeconds() == null ? 3600 : mailConsumerProperties.getSenderCooldownSeconds();
    }
}
