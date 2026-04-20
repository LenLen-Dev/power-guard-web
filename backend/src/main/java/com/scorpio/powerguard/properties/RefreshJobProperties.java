package com.scorpio.powerguard.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "refresh-job")
public class RefreshJobProperties {

    private String queueKey;
    private String deadLetterQueueKey;
    private String activeJobKey;
    private String manualQueueKey;
    private String runtimeKeyPrefix;
    private Integer consumerConcurrency;
    private Integer redisTtlHours;
}
