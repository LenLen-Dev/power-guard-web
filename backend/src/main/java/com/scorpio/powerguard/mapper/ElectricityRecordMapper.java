package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.ElectricityRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface ElectricityRecordMapper {

    int insert(ElectricityRecord record);

    int deleteByRoomId(@Param("roomId") Long roomId);

    ElectricityRecord selectNearestSnapshot(@Param("roomId") Long roomId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("targetTime") LocalDateTime targetTime);
}
