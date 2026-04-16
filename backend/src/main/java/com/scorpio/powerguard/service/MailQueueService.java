package com.scorpio.powerguard.service;

import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.model.ExternalElectricityResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MailQueueService {

    void enqueueLowBalanceAlert(Room room, ExternalElectricityResult result, LocalDateTime fetchTime);

    void enqueueDailySummary(Room room, BigDecimal todayUsage, LocalDateTime fetchTime);

    void processDeferredQuietAlerts();

    void clearDailySendCounters();
}
