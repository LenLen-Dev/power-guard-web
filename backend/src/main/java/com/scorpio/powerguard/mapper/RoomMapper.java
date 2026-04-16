package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.Room;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoomMapper {

    int insert(Room room);

    int updateBasicInfo(Room room);

    int updateElectricityData(@Param("id") Long id,
                              @Param("total") BigDecimal total,
                              @Param("remain") BigDecimal remain,
                              @Param("status") Integer status,
                              @Param("updateTime") LocalDateTime updateTime);

    Room selectById(@Param("id") Long id);

    Room selectByIdIncludingDeleted(@Param("id") Long id);

    List<Room> selectAllActive();

    List<Room> selectAllActiveOrderByRoom();

    Room selectByBuildingAndRoom(@Param("buildingId") String buildingId,
                                 @Param("roomId") String roomId,
                                 @Param("excludeId") Long excludeId);

    int deleteById(@Param("id") Long id);
}
