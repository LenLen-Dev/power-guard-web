package com.scorpio.powerguard.schedule;

import com.scorpio.powerguard.service.MailQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeferredAlertScheduler {

    private final MailQueueService mailQueueService;

    @Scheduled(cron = "0 0 7 * * ?")
    public void processDeferredQuietAlerts() {
        log.info("Trigger deferred quiet-hour alert task");
        mailQueueService.processDeferredQuietAlerts();
    }
}
