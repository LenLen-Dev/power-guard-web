package com.scorpio.powerguard.schedule;

import com.scorpio.powerguard.service.LotteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryDrawScheduler {

    private final LotteryService lotteryService;

    @Scheduled(cron = "0 0 12 1,15 * ?", zone = "Asia/Shanghai")
    public void executeDraw() {
        log.info("Trigger scheduled lottery draw task");
        lotteryService.executeScheduledDraw();
    }
}
