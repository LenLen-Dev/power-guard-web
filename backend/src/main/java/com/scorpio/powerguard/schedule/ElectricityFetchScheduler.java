package com.scorpio.powerguard.schedule;

import com.scorpio.powerguard.service.ElectricityFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElectricityFetchScheduler {

    private final ElectricityFetchService electricityFetchService;

    @Scheduled(cron = "0 0 */2 * * ?")
    public void fetchElectricity() {
        log.info("Trigger scheduled electricity fetch task");
        electricityFetchService.submitScheduledRefreshIfIdle();
    }
}
