package com.scorpio.powerguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scorpio.powerguard.entity.RefreshJob;
import com.scorpio.powerguard.mapper.RefreshJobItemMapper;
import com.scorpio.powerguard.mapper.RefreshJobMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.properties.RefreshJobProperties;
import com.scorpio.powerguard.service.impl.ElectricityFetchServiceImpl;
import com.scorpio.powerguard.vo.RefreshJobVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ElectricityFetchServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RefreshJobMapper refreshJobMapper;

    @Mock
    private RefreshJobItemMapper refreshJobItemMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ElectricityFetchServiceImpl electricityFetchService;
    private AtomicReference<RefreshJob> storedJob;

    @BeforeEach
    void setUp() {
        RefreshJobProperties refreshJobProperties = new RefreshJobProperties();
        refreshJobProperties.setQueueKey("power:refresh:room");
        refreshJobProperties.setDeadLetterQueueKey("power:refresh:room:dead-letter");
        refreshJobProperties.setActiveJobKey("power:refresh:active");
        refreshJobProperties.setManualQueueKey("power:refresh:manual:queue");
        refreshJobProperties.setRuntimeKeyPrefix("power:refresh:job");
        refreshJobProperties.setConsumerConcurrency(4);
        refreshJobProperties.setRedisTtlHours(24);

        storedJob = new AtomicReference<>();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(refreshJobMapper.selectByJobId(anyString())).thenAnswer(invocation -> storedJob.get());
        lenient().doAnswer(invocation -> {
            RefreshJob job = invocation.getArgument(0);
            storedJob.set(copyJob(job));
            return 1;
        }).when(refreshJobMapper).insert(any(RefreshJob.class));
        lenient().doAnswer(invocation -> {
            RefreshJob job = storedJob.get();
            if (job == null || !"QUEUED".equals(job.getStatus())) {
                return 0;
            }
            job.setStatus("RUNNING");
            job.setTotalRooms(invocation.getArgument(1, Integer.class));
            job.setStartedAt(invocation.getArgument(2, LocalDateTime.class));
            job.setMessage(invocation.getArgument(3, String.class));
            job.setUpdateTime(invocation.getArgument(4, LocalDateTime.class));
            return 1;
        }).when(refreshJobMapper).markRunning(anyString(), any(), any(), anyString(), any());
        lenient().doAnswer(invocation -> {
            RefreshJob job = storedJob.get();
            job.setCompletedRooms(job.getCompletedRooms() + invocation.getArgument(1, Integer.class));
            job.setSuccessRooms(job.getSuccessRooms() + invocation.getArgument(2, Integer.class));
            job.setFailedRooms(job.getFailedRooms() + invocation.getArgument(3, Integer.class));
            job.setMessage(invocation.getArgument(4, String.class));
            job.setUpdateTime(invocation.getArgument(5, LocalDateTime.class));
            return 1;
        }).when(refreshJobMapper).incrementProgress(anyString(), any(), any(), any(), anyString(), any());
        lenient().doAnswer(invocation -> {
            RefreshJob job = storedJob.get();
            if (job == null || !"RUNNING".equals(job.getStatus())) {
                return 0;
            }
            job.setStatus(invocation.getArgument(1, String.class));
            job.setFinishedAt(invocation.getArgument(2, LocalDateTime.class));
            job.setMessage(invocation.getArgument(3, String.class));
            job.setUpdateTime(invocation.getArgument(4, LocalDateTime.class));
            return 1;
        }).when(refreshJobMapper).finishIfRunning(anyString(), anyString(), any(), anyString(), any());
        lenient().doAnswer(invocation -> {
            RefreshJob job = storedJob.get();
            if (job == null || (!"QUEUED".equals(job.getStatus()) && !"RUNNING".equals(job.getStatus()))) {
                return 0;
            }
            job.setStatus(invocation.getArgument(1, String.class));
            job.setFinishedAt(invocation.getArgument(2, LocalDateTime.class));
            job.setMessage(invocation.getArgument(3, String.class));
            job.setUpdateTime(invocation.getArgument(4, LocalDateTime.class));
            return 1;
        }).when(refreshJobMapper).failIfPending(anyString(), anyString(), any(), anyString(), any());

        electricityFetchService = new ElectricityFetchServiceImpl(
            roomMapper,
            refreshJobMapper,
            refreshJobItemMapper,
            stringRedisTemplate,
            rabbitTemplate,
            refreshJobProperties
        );
    }

    @Test
    void shouldQueueManualRefreshWhenAnotherJobIsActive() {
        when(valueOperations.setIfAbsent(eq("power:refresh:active"), anyString(), eq(Duration.ofHours(24))))
            .thenReturn(false);

        RefreshJobVO response = electricityFetchService.submitManualRefresh();

        assertEquals("QUEUED", response.getStatus());
        verify(listOperations).rightPush("power:refresh:manual:queue", response.getJobId());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void shouldSkipScheduledRefreshWhenPendingJobExists() {
        when(refreshJobMapper.countByStatuses(any())).thenReturn(1);

        electricityFetchService.submitScheduledRefreshIfIdle();

        verify(refreshJobMapper, never()).insert(any(RefreshJob.class));
    }

    @Test
    void shouldReturnLatestRefreshJob() {
        RefreshJob latest = new RefreshJob();
        latest.setJobId("latest-job");
        latest.setSource("SCHEDULED");
        latest.setStatus("SUCCESS");
        latest.setTotalRooms(41);
        latest.setCompletedRooms(41);
        latest.setSuccessRooms(41);
        latest.setFailedRooms(0);
        latest.setQueuedAt(LocalDateTime.now());

        when(refreshJobMapper.selectLatest()).thenReturn(latest);

        RefreshJobVO response = electricityFetchService.getLatestRefreshJob();

        assertEquals("latest-job", response.getJobId());
        assertEquals("SCHEDULED", response.getSource());
        assertEquals(41, response.getSuccessRooms());
    }

    @Test
    void shouldFinishRefreshJobAsSuccessAfterLastSuccessItem() {
        RefreshJob job = new RefreshJob();
        job.setJobId("job-1");
        job.setSource("MANUAL");
        job.setStatus("RUNNING");
        job.setTotalRooms(1);
        job.setCompletedRooms(0);
        job.setSuccessRooms(0);
        job.setFailedRooms(0);
        job.setQueuedAt(LocalDateTime.now());
        storedJob.set(job);

        when(refreshJobItemMapper.markSuccess(eq("job-1"), eq(1L), any())).thenReturn(1);
        when(valueOperations.get("power:refresh:active")).thenReturn("job-1");
        when(stringRedisTemplate.hasKey("power:refresh:active")).thenReturn(false);

        electricityFetchService.recordJobItemSuccess("job-1", 1L);

        assertEquals("SUCCESS", storedJob.get().getStatus());
        verify(stringRedisTemplate).delete("power:refresh:active");
    }

    @Test
    void shouldFinishRefreshJobAsPartialSuccessAfterLastFailureItem() {
        RefreshJob job = new RefreshJob();
        job.setJobId("job-2");
        job.setSource("MANUAL");
        job.setStatus("RUNNING");
        job.setTotalRooms(2);
        job.setCompletedRooms(1);
        job.setSuccessRooms(1);
        job.setFailedRooms(0);
        job.setQueuedAt(LocalDateTime.now());
        storedJob.set(job);

        when(refreshJobItemMapper.markFailed(eq("job-2"), eq(2L), anyString(), any())).thenReturn(1);
        when(valueOperations.get("power:refresh:active")).thenReturn("job-2");
        when(stringRedisTemplate.hasKey("power:refresh:active")).thenReturn(false);

        electricityFetchService.recordJobItemFailure("job-2", 2L, "timeout");

        assertEquals("PARTIAL_SUCCESS", storedJob.get().getStatus());
        assertEquals(1, storedJob.get().getSuccessRooms());
        assertEquals(1, storedJob.get().getFailedRooms());
    }

    @Test
    void shouldClaimPendingRefreshItemOnlyOnce() {
        when(refreshJobItemMapper.claimPending(eq("job-3"), eq(3L), eq("RUNNING"), any())).thenReturn(1).thenReturn(0);

        assertTrue(electricityFetchService.claimJobItem("job-3", 3L));
        assertFalse(electricityFetchService.claimJobItem("job-3", 3L));
    }

    private RefreshJob copyJob(RefreshJob source) {
        RefreshJob copy = new RefreshJob();
        copy.setId(source.getId());
        copy.setJobId(source.getJobId());
        copy.setSource(source.getSource());
        copy.setStatus(source.getStatus());
        copy.setTotalRooms(source.getTotalRooms());
        copy.setCompletedRooms(source.getCompletedRooms());
        copy.setSuccessRooms(source.getSuccessRooms());
        copy.setFailedRooms(source.getFailedRooms());
        copy.setQueuedAt(source.getQueuedAt());
        copy.setStartedAt(source.getStartedAt());
        copy.setFinishedAt(source.getFinishedAt());
        copy.setMessage(source.getMessage());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }
}
