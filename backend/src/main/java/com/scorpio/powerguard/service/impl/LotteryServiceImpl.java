package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.entity.LotteryDraw;
import com.scorpio.powerguard.entity.LotteryDrawWinner;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.mapper.LotteryDrawMapper;
import com.scorpio.powerguard.mapper.LotteryDrawWinnerMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.service.LotteryService;
import com.scorpio.powerguard.vo.LotteryDrawVO;
import com.scorpio.powerguard.vo.LotteryWinnerVO;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryServiceImpl implements LotteryService {

    static final int WINNER_LIMIT = 3;
    private static final BigDecimal EMAIL_REWARD = new BigDecimal("15.00");
    private static final BigDecimal DEFAULT_REWARD = new BigDecimal("7.50");
    private static final DateTimeFormatter DRAW_KEY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId LOTTERY_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RoomMapper roomMapper;
    private final LotteryDrawMapper lotteryDrawMapper;
    private final LotteryDrawWinnerMapper lotteryDrawWinnerMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeScheduledDraw() {
        LocalDateTime drawTime = currentDrawTime();
        String drawKey = drawTime.toLocalDate().format(DRAW_KEY_FORMATTER);
        if (lotteryDrawMapper.selectByDrawKey(drawKey) != null) {
            log.info("Skip lottery draw because draw already exists for key={}", drawKey);
            return;
        }

        List<Room> activeRooms = roomMapper.selectAllActive();
        if (activeRooms == null || activeRooms.isEmpty()) {
            log.info("Skip lottery draw because there are no active rooms");
            return;
        }

        List<Room> winners = pickWinners(activeRooms);
        LotteryDraw draw = new LotteryDraw();
        draw.setDrawKey(drawKey);
        draw.setDrawTime(drawTime);
        draw.setWinnerCount(winners.size());
        draw.setMessage(buildDrawMessage(winners.size()));
        draw.setCreateTime(LocalDateTime.now(LOTTERY_ZONE_ID));
        lotteryDrawMapper.insert(draw);

        lotteryDrawWinnerMapper.batchInsert(buildWinnerEntities(draw.getId(), winners, draw.getCreateTime()));
        log.info("Finish lottery draw for key={}, winnerCount={}", drawKey, winners.size());
    }

    @Override
    public LotteryDrawVO getLatestDraw() {
        LotteryDraw latestDraw = lotteryDrawMapper.selectLatest();
        if (latestDraw == null) {
            return null;
        }
        List<LotteryDrawWinner> winners = lotteryDrawWinnerMapper.selectByDrawId(latestDraw.getId());
        LotteryDrawVO vo = new LotteryDrawVO();
        vo.setDrawKey(latestDraw.getDrawKey());
        vo.setDrawTime(latestDraw.getDrawTime());
        vo.setWinnerCount(latestDraw.getWinnerCount());
        vo.setMessage(latestDraw.getMessage());
        vo.setWinners(winners.stream().map(this::toWinnerVO).toList());
        return vo;
    }

    private LocalDateTime currentDrawTime() {
        LocalDate today = LocalDate.now(LOTTERY_ZONE_ID);
        return LocalDateTime.of(today, LocalTime.NOON);
    }

    private List<Room> pickWinners(List<Room> activeRooms) {
        List<Room> candidates = new ArrayList<>(activeRooms);
        Collections.shuffle(candidates, secureRandom);
        return candidates.subList(0, Math.min(WINNER_LIMIT, candidates.size()));
    }

    private List<LotteryDrawWinner> buildWinnerEntities(Long drawId, List<Room> winners, LocalDateTime createTime) {
        List<LotteryDrawWinner> entities = new ArrayList<>(winners.size());
        for (int i = 0; i < winners.size(); i++) {
            Room room = winners.get(i);
            LotteryDrawWinner winner = new LotteryDrawWinner();
            winner.setDrawId(drawId);
            winner.setRoomPkId(room.getId());
            winner.setWinnerRank(i + 1);
            winner.setBuildingId(room.getBuildingId());
            winner.setBuildingName(room.getBuildingName());
            winner.setRoomId(room.getRoomId());
            winner.setRoomName(room.getRoomName());
            winner.setAlertEmail(room.getAlertEmail());
            winner.setRewardAmount(hasAlertEmail(room) ? EMAIL_REWARD : DEFAULT_REWARD);
            winner.setCreateTime(createTime);
            entities.add(winner);
        }
        return entities;
    }

    private LotteryWinnerVO toWinnerVO(LotteryDrawWinner winner) {
        LotteryWinnerVO vo = new LotteryWinnerVO();
        vo.setWinnerRank(winner.getWinnerRank());
        vo.setBuildingName(winner.getBuildingName());
        vo.setRoomId(winner.getRoomId());
        vo.setRoomName(winner.getRoomName());
        vo.setRewardAmount(winner.getRewardAmount());
        return vo;
    }

    private boolean hasAlertEmail(Room room) {
        return room != null && room.getAlertEmail() != null && !room.getAlertEmail().isBlank();
    }

    private String buildDrawMessage(int winnerCount) {
        return "本期开奖完成，共抽出 %d 个幸运宿舍".formatted(winnerCount);
    }
}
