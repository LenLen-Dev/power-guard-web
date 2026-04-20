package com.scorpio.powerguard.controller;

import com.scorpio.powerguard.common.ApiResponse;
import com.scorpio.powerguard.dto.RoomCreateRequest;
import com.scorpio.powerguard.dto.RoomUpdateRequest;
import com.scorpio.powerguard.service.ElectricityFetchService;
import com.scorpio.powerguard.service.RoomService;
import com.scorpio.powerguard.vo.DailyTrendVO;
import com.scorpio.powerguard.vo.RefreshJobVO;
import com.scorpio.powerguard.vo.RoomStatusVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final ElectricityFetchService electricityFetchService;
    private final RoomService roomService;

    @PostMapping
    public ApiResponse<RoomStatusVO> createRoom(@Valid @RequestBody RoomCreateRequest request) {
        return ApiResponse.success("新增成功", roomService.createRoom(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomStatusVO> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return ApiResponse.success("更新成功", roomService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshJobVO> refreshRoomStatus() {
        return ApiResponse.success("任务已提交", electricityFetchService.submitManualRefresh());
    }

    @GetMapping("/refresh/{jobId}")
    public ApiResponse<RefreshJobVO> getRefreshJob(@PathVariable String jobId) {
        return ApiResponse.success(electricityFetchService.getRefreshJob(jobId));
    }

    @GetMapping("/refresh/latest")
    public ApiResponse<RefreshJobVO> getLatestRefreshJob() {
        return ApiResponse.success(electricityFetchService.getLatestRefreshJob());
    }

    @GetMapping("/status")
    public ApiResponse<List<RoomStatusVO>> listRoomStatus() {
        return ApiResponse.success(roomService.listRoomStatus());
    }

    @GetMapping("/{id}/trend")
    public ApiResponse<List<DailyTrendVO>> queryTrend(@PathVariable Long id,
                                                      @RequestParam(value = "days", required = false, defaultValue = "7") Integer days) {
        return ApiResponse.success(roomService.queryRoomTrend(id, days));
    }
}
