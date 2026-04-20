package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.RefreshJob;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RefreshJobMapper {

    int insert(RefreshJob refreshJob);

    RefreshJob selectByJobId(@Param("jobId") String jobId);

    RefreshJob selectLatest();

    int countByStatuses(@Param("statuses") List<String> statuses);

    int markRunning(@Param("jobId") String jobId,
                    @Param("totalRooms") Integer totalRooms,
                    @Param("startedAt") LocalDateTime startedAt,
                    @Param("message") String message,
                    @Param("updateTime") LocalDateTime updateTime);

    int incrementProgress(@Param("jobId") String jobId,
                          @Param("completedIncrement") Integer completedIncrement,
                          @Param("successIncrement") Integer successIncrement,
                          @Param("failedIncrement") Integer failedIncrement,
                          @Param("message") String message,
                          @Param("updateTime") LocalDateTime updateTime);

    int finishIfRunning(@Param("jobId") String jobId,
                        @Param("status") String status,
                        @Param("finishedAt") LocalDateTime finishedAt,
                        @Param("message") String message,
                        @Param("updateTime") LocalDateTime updateTime);

    int failIfPending(@Param("jobId") String jobId,
                      @Param("status") String status,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("message") String message,
                      @Param("updateTime") LocalDateTime updateTime);
}
