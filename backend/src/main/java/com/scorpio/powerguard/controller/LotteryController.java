package com.scorpio.powerguard.controller;

import com.scorpio.powerguard.common.ApiResponse;
import com.scorpio.powerguard.service.LotteryService;
import com.scorpio.powerguard.vo.LotteryDrawVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lottery")
@RequiredArgsConstructor
public class LotteryController {

    private final LotteryService lotteryService;

    @GetMapping("/latest")
    public ApiResponse<LotteryDrawVO> getLatestDraw() {
        return ApiResponse.success(lotteryService.getLatestDraw());
    }
}
