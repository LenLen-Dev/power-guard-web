package com.scorpio.powerguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.LotteryDraw;
import com.scorpio.powerguard.entity.LotteryDrawWinner;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.mapper.LotteryDrawMapper;
import com.scorpio.powerguard.mapper.LotteryDrawWinnerMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.service.impl.LotteryServiceImpl;
import com.scorpio.powerguard.vo.LotteryDrawVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotteryServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private LotteryDrawMapper lotteryDrawMapper;

    @Mock
    private LotteryDrawWinnerMapper lotteryDrawWinnerMapper;

    private LotteryServiceImpl lotteryService;

    @BeforeEach
    void setUp() {
        lotteryService = new LotteryServiceImpl(roomMapper, lotteryDrawMapper, lotteryDrawWinnerMapper);
    }

    @Test
    void shouldSkipDrawWhenNoActiveRooms() {
        when(lotteryDrawMapper.selectByDrawKey(any())).thenReturn(null);
        when(roomMapper.selectAllActive()).thenReturn(List.of());

        lotteryService.executeScheduledDraw();

        verify(lotteryDrawMapper, never()).insert(any(LotteryDraw.class));
        verify(lotteryDrawWinnerMapper, never()).batchInsert(any());
    }

    @Test
    void shouldSkipDrawWhenDrawAlreadyExists() {
        LotteryDraw existingDraw = new LotteryDraw();
        existingDraw.setId(1L);
        when(lotteryDrawMapper.selectByDrawKey(any())).thenReturn(existingDraw);

        lotteryService.executeScheduledDraw();

        verify(roomMapper, never()).selectAllActive();
        verify(lotteryDrawWinnerMapper, never()).batchInsert(any());
    }

    @Test
    void shouldCreateWinnersAndRewardsWhenRoomCountIsLessThanLimit() {
        Room roomA = buildRoom(1L, "男19#楼", "215", "男19#楼-215", "a@example.com");
        Room roomB = buildRoom(2L, "男19#楼", "216", "男19#楼-216", "");
        when(lotteryDrawMapper.selectByDrawKey(any())).thenReturn(null);
        when(roomMapper.selectAllActive()).thenReturn(List.of(roomA, roomB));

        LotteryDraw storedDraw = new LotteryDraw();
        storedDraw.setId(88L);
        when(lotteryDrawMapper.insert(any(LotteryDraw.class))).thenAnswer(invocation -> {
            LotteryDraw draw = invocation.getArgument(0, LotteryDraw.class);
            draw.setId(storedDraw.getId());
            return 1;
        });

        lotteryService.executeScheduledDraw();

        ArgumentCaptor<LotteryDraw> drawCaptor = ArgumentCaptor.forClass(LotteryDraw.class);
        verify(lotteryDrawMapper).insert(drawCaptor.capture());
        assertEquals(2, drawCaptor.getValue().getWinnerCount());

        ArgumentCaptor<List<LotteryDrawWinner>> winnersCaptor = ArgumentCaptor.forClass(List.class);
        verify(lotteryDrawWinnerMapper).batchInsert(winnersCaptor.capture());
        List<LotteryDrawWinner> winners = winnersCaptor.getValue();
        assertEquals(2, winners.size());
        assertEquals(
            List.of(new BigDecimal("7.50"), new BigDecimal("15.00")),
            winners.stream()
                .map(LotteryDrawWinner::getRewardAmount)
                .sorted()
                .collect(Collectors.toList())
        );
        assertEquals(List.of(1L, 2L), winners.stream().map(LotteryDrawWinner::getRoomPkId).sorted().toList());
        assertEquals(List.of(1, 2), winners.stream().map(LotteryDrawWinner::getWinnerRank).toList());
    }

    @Test
    void shouldPickAtMostThreeUniqueWinnersWhenRoomsExceedLimit() {
        List<Room> rooms = List.of(
            buildRoom(1L, "男19#楼", "215", "男19#楼-215", "a@example.com"),
            buildRoom(2L, "男19#楼", "216", "男19#楼-216", ""),
            buildRoom(3L, "男19#楼", "217", "男19#楼-217", "b@example.com"),
            buildRoom(4L, "男19#楼", "218", "男19#楼-218", "")
        );
        when(lotteryDrawMapper.selectByDrawKey(any())).thenReturn(null);
        when(roomMapper.selectAllActive()).thenReturn(rooms);
        when(lotteryDrawMapper.insert(any(LotteryDraw.class))).thenAnswer(invocation -> {
            LotteryDraw draw = invocation.getArgument(0, LotteryDraw.class);
            draw.setId(99L);
            return 1;
        });

        lotteryService.executeScheduledDraw();

        ArgumentCaptor<List<LotteryDrawWinner>> winnersCaptor = ArgumentCaptor.forClass(List.class);
        verify(lotteryDrawWinnerMapper).batchInsert(winnersCaptor.capture());
        List<LotteryDrawWinner> winners = winnersCaptor.getValue();
        assertEquals(3, winners.size());
        assertEquals(3, winners.stream().map(LotteryDrawWinner::getRoomPkId).distinct().count());
        assertTrue(rooms.stream().map(Room::getId).toList().containsAll(winners.stream().map(LotteryDrawWinner::getRoomPkId).toList()));
    }

    @Test
    void shouldReturnLatestDrawWithWinners() {
        LotteryDraw latestDraw = new LotteryDraw();
        latestDraw.setId(10L);
        latestDraw.setDrawKey("20260501");
        latestDraw.setDrawTime(LocalDateTime.of(2026, 5, 1, 12, 0));
        latestDraw.setWinnerCount(2);
        latestDraw.setMessage("本期开奖完成，共抽出 2 个幸运宿舍");

        LotteryDrawWinner winner = new LotteryDrawWinner();
        winner.setWinnerRank(1);
        winner.setBuildingName("男19#楼");
        winner.setRoomId("215");
        winner.setRoomName("男19#楼-215");
        winner.setRewardAmount(new BigDecimal("15.00"));

        when(lotteryDrawMapper.selectLatest()).thenReturn(latestDraw);
        when(lotteryDrawWinnerMapper.selectByDrawId(10L)).thenReturn(List.of(winner));

        LotteryDrawVO response = lotteryService.getLatestDraw();

        assertNotNull(response);
        assertEquals("20260501", response.getDrawKey());
        assertEquals(1, response.getWinners().size());
        assertEquals("男19#楼", response.getWinners().get(0).getBuildingName());
        assertEquals(new BigDecimal("15.00"), response.getWinners().get(0).getRewardAmount());
    }

    @Test
    void shouldReturnNullWhenNoLatestDrawExists() {
        when(lotteryDrawMapper.selectLatest()).thenReturn(null);

        LotteryDrawVO response = lotteryService.getLatestDraw();

        assertNull(response);
    }

    private Room buildRoom(Long id, String buildingName, String roomId, String roomName, String alertEmail) {
        Room room = new Room();
        room.setId(id);
        room.setBuildingId("10");
        room.setBuildingName(buildingName);
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setAlertEmail(alertEmail);
        return room;
    }
}
