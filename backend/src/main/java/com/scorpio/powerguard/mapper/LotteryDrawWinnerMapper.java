package com.scorpio.powerguard.mapper;

import com.scorpio.powerguard.entity.LotteryDrawWinner;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LotteryDrawWinnerMapper {

    int batchInsert(@Param("winners") List<LotteryDrawWinner> winners);

    List<LotteryDrawWinner> selectByDrawId(@Param("drawId") Long drawId);
}
