package com.scorpio.powerguard.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mail-consumer")
public class MailConsumerProperties {

    private String queueKey;
    private String deadLetterKey;
    private String senderCountKeyPrefix;
    private Integer maxDailySendCount;
    private Integer maxRetryCount;
    private Long idleWaitMillis;
}
