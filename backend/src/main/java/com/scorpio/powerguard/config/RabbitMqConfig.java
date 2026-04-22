package com.scorpio.powerguard.config;

import com.scorpio.powerguard.properties.MailConsumerProperties;
import com.scorpio.powerguard.properties.RefreshJobProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final RefreshJobProperties refreshJobProperties;
    private final MailConsumerProperties mailConsumerProperties;

    @Bean
    public Queue refreshJobQueue() {
        return QueueBuilder.durable(refreshJobProperties.getQueueKey())
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", refreshJobProperties.getDeadLetterQueueKey())
            .build();
    }

    @Bean
    public Queue refreshJobDeadLetterQueue() {
        return QueueBuilder.durable(refreshJobProperties.getDeadLetterQueueKey()).build();
    }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(mailConsumerProperties.getQueueKey())
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", mailConsumerProperties.getDeadLetterKey())
            .build();
    }

    @Bean
    public Queue mailRetryQueue() {
        int retryDelaySeconds = mailConsumerProperties.getRetryDelaySeconds() == null ? 10 : mailConsumerProperties.getRetryDelaySeconds();
        long retryDelayMillis = Math.max(1, retryDelaySeconds) * 1000L;
        return QueueBuilder.durable(mailConsumerProperties.getRetryQueueKey())
            .withArgument("x-message-ttl", retryDelayMillis)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", mailConsumerProperties.getQueueKey())
            .build();
    }

    @Bean
    public Queue mailAuthRetryQueue() {
        int authRetryDelaySeconds = mailConsumerProperties.getAuthRetryDelaySeconds() == null
            ? 3600
            : mailConsumerProperties.getAuthRetryDelaySeconds();
        long authRetryDelayMillis = Math.max(1, authRetryDelaySeconds) * 1000L;
        return QueueBuilder.durable(mailConsumerProperties.getAuthRetryQueueKey())
            .withArgument("x-message-ttl", authRetryDelayMillis)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", mailConsumerProperties.getQueueKey())
            .build();
    }

    @Bean
    public Queue mailDeadLetterQueue() {
        return QueueBuilder.durable(mailConsumerProperties.getDeadLetterKey()).build();
    }
}
