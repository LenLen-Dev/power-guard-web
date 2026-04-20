package com.scorpio.powerguard.service;

import com.scorpio.powerguard.vo.LotteryDrawVO;

public interface LotteryService {

    void executeScheduledDraw();

    LotteryDrawVO getLatestDraw();
}
