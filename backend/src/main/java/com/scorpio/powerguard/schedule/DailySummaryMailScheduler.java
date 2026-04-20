package com.scorpio.powerguard.schedule;

import com.scorpio.powerguard.service.DailySummaryMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryMailScheduler {

    private final DailySummaryMailService dailySummaryMailService;

    @Scheduled(cron = "0 46 18 * * ?")
    public void sendDailySummaries() {
        log.info("Trigger daily summary mail task");
        dailySummaryMailService.sendDailySummaries();
    }
}
