package com.scorpio.powerguard.service;

import com.scorpio.powerguard.vo.RefreshJobVO;

public interface ElectricityFetchService {

    RefreshJobVO submitManualRefresh();

    void submitScheduledRefreshIfIdle();

    RefreshJobVO getRefreshJob(String jobId);

    RefreshJobVO getLatestRefreshJob();
}
