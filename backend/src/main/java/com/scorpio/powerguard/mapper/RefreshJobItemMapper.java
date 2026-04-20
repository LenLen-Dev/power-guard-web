package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.RefreshJobItem;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RefreshJobItemMapper {

    int batchInsert(@Param("items") List<RefreshJobItem> items);

    int claimPending(@Param("jobId") String jobId,
                     @Param("roomId") Long roomId,
                     @Param("status") String status,
                     @Param("startedAt") LocalDateTime startedAt);

    int markSuccess(@Param("jobId") String jobId,
                    @Param("roomId") Long roomId,
                    @Param("finishedAt") LocalDateTime finishedAt);

    int markFailed(@Param("jobId") String jobId,
                   @Param("roomId") Long roomId,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);
}
