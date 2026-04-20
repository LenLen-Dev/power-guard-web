package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.LotteryDraw;
import org.apache.ibatis.annotations.Param;

public interface LotteryDrawMapper {

    int insert(LotteryDraw lotteryDraw);

    LotteryDraw selectByDrawKey(@Param("drawKey") String drawKey);

    LotteryDraw selectLatest();
}
