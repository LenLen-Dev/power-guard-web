package com.scorpio.powerguard.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mail-consumer")
public class MailConsumerProperties {

    private String queueKey;
    private String retryQueueKey;
    private String deadLetterKey;
    private String senderCountKeyPrefix;
    private Integer maxDailySendCount;
    private Integer maxRetryCount;
    private Integer retryDelaySeconds;
    private Integer listenerConcurrency;
    private Integer shortPauseMinSeconds;
    private Integer shortPauseMaxSeconds;
    private Integer longPauseEveryAttempts;
    private Integer longPauseMinSeconds;
    private Integer longPauseMaxSeconds;
    private Integer senderSwitchEveryAttempts;
}
