package com.scorpio.powerguard.service;

import com.scorpio.powerguard.dto.RoomCreateRequest;
import com.scorpio.powerguard.dto.RoomUpdateRequest;
import com.scorpio.powerguard.vo.DailyTrendVO;
import com.scorpio.powerguard.vo.RoomStatusVO;
import java.util.List;

public interface RoomService {

    RoomStatusVO createRoom(RoomCreateRequest request);

    RoomStatusVO updateRoom(Long id, RoomUpdateRequest request);

    void deleteRoom(Long id);

    List<RoomStatusVO> listRoomStatus();

    List<DailyTrendVO> queryRoomTrend(Long roomId, Integer days);
}
