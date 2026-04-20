package com.scorpio.powerguard.service.impl;

import com.scorpio.powerguard.entity.RefreshJob;
import com.scorpio.powerguard.entity.RefreshJobItem;
import com.scorpio.powerguard.entity.Room;
import com.scorpio.powerguard.enums.RefreshJobItemStatusEnum;
import com.scorpio.powerguard.enums.RefreshJobSourceEnum;
import com.scorpio.powerguard.enums.RefreshJobStatusEnum;
import com.scorpio.powerguard.exception.BusinessException;
import com.scorpio.powerguard.mapper.RefreshJobItemMapper;
import com.scorpio.powerguard.mapper.RefreshJobMapper;
import com.scorpio.powerguard.mapper.RoomMapper;
import com.scorpio.powerguard.model.RefreshRoomMessage;
import com.scorpio.powerguard.properties.RefreshJobProperties;
import com.scorpio.powerguard.service.ElectricityFetchService;
import com.scorpio.powerguard.util.JsonUtils;
import com.scorpio.powerguard.vo.RefreshJobVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectricityFetchServiceImpl implements ElectricityFetchService {

    private static final List<String> PENDING_JOB_STATUSES = List.of(
        RefreshJobStatusEnum.QUEUED.name(),
        RefreshJobStatusEnum.RUNNING.name()
    );

    private final RoomMapper roomMapper;
    private final RefreshJobMapper refreshJobMapper;
    private final RefreshJobItemMapper refreshJobItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RefreshJobProperties refreshJobProperties;

    @Override
    public RefreshJobVO submitManualRefresh() {
        RefreshJob refreshJob = createQueuedJob(RefreshJobSourceEnum.MANUAL);
        syncRuntimeState(refreshJobMapper.selectByJobId(refreshJob.getJobId()));

        if (!tryActivateJob(refreshJob.getJobId())) {
            stringRedisTemplate.opsForList().rightPush(refreshJobProperties.getManualQueueKey(), refreshJob.getJobId());
        }

        return getRefreshJob(refreshJob.getJobId());
    }

    @Override
    public void submitScheduledRefreshIfIdle() {
        if (refreshJobMapper.countByStatuses(PENDING_JOB_STATUSES) > 0) {
            log.info("Skip scheduled refresh because another refresh job is queued or running");
            return;
        }

        RefreshJob refreshJob = createQueuedJob(RefreshJobSourceEnum.SCHEDULED);
        syncRuntimeState(refreshJobMapper.selectByJobId(refreshJob.getJobId()));

        if (!tryActivateJob(refreshJob.getJobId())) {
            markJobFailed(refreshJob.getJobId(), "定时刷新已跳过，当前已有其他刷新任务");
        }
    }

    @Override
    public RefreshJobVO getRefreshJob(String jobId) {
        RefreshJob refreshJob = refreshJobMapper.selectByJobId(jobId);
        if (refreshJob == null) {
            throw new BusinessException(404, "刷新任务不存在");
        }
        return toVO(refreshJob);
    }

    @Override
    public RefreshJobVO getLatestRefreshJob() {
        RefreshJob refreshJob = refreshJobMapper.selectLatest();
        if (refreshJob == null) {
            return null;
        }
        return toVO(refreshJob);
    }

    public boolean claimJobItem(String jobId, Long roomId) {
        return refreshJobItemMapper.claimPending(
            jobId,
            roomId,
            RefreshJobItemStatusEnum.RUNNING.name(),
            LocalDateTime.now()
        ) > 0;
    }

    public void recordJobItemSuccess(String jobId, Long roomId) {
        RefreshJob refreshJob = updateJobItemOutcome(jobId, roomId, true, null);
        finalizeJobIfNecessary(refreshJob);
    }

    public void recordJobItemFailure(String jobId, Long roomId, String errorMessage) {
        RefreshJob refreshJob = updateJobItemOutcome(jobId, roomId, false, errorMessage);
        finalizeJobIfNecessary(refreshJob);
    }

    protected RefreshJob createQueuedJob(RefreshJobSourceEnum source) {
        LocalDateTime now = LocalDateTime.now();
        RefreshJob refreshJob = new RefreshJob();
        refreshJob.setJobId(UUID.randomUUID().toString().replace("-", ""));
        refreshJob.setSource(source.name());
        refreshJob.setStatus(RefreshJobStatusEnum.QUEUED.name());
        refreshJob.setTotalRooms(0);
        refreshJob.setCompletedRooms(0);
        refreshJob.setSuccessRooms(0);
        refreshJob.setFailedRooms(0);
        refreshJob.setQueuedAt(now);
        refreshJob.setMessage(source == RefreshJobSourceEnum.MANUAL ? "刷新任务已提交，等待执行" : "定时刷新任务已提交");
        refreshJob.setUpdateTime(now);
        refreshJobMapper.insert(refreshJob);
        return refreshJob;
    }

    protected RefreshJob updateJobItemOutcome(String jobId, Long roomId, boolean success, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        int updated = success
            ? refreshJobItemMapper.markSuccess(jobId, roomId, now)
            : refreshJobItemMapper.markFailed(jobId, roomId, sanitizeMessage(errorMessage), now);
        if (updated == 0) {
            return null;
        }

        refreshJobMapper.incrementProgress(
            jobId,
            1,
            success ? 1 : 0,
            success ? 0 : 1,
            success ? "房间刷新进行中" : sanitizeMessage(errorMessage),
            now
        );

        RefreshJob refreshJob = refreshJobMapper.selectByJobId(jobId);
        syncRuntimeState(refreshJob);
        return refreshJob;
    }

    private boolean tryActivateJob(String jobId) {
        if (!claimActiveJob(jobId)) {
            return false;
        }

        activateJobSafely(jobId);
        return true;
    }

    private void activateJobSafely(String jobId) {
        try {
            activateJob(jobId);
        } catch (Exception ex) {
            log.error("Failed to activate refresh job {}", jobId, ex);
            markJobFailed(jobId, "刷新任务启动失败: " + sanitizeMessage(ex.getMessage()));
        }
    }

    private void activateJob(String jobId) {
        List<Room> rooms = roomMapper.selectAllActive();
        LocalDateTime now = LocalDateTime.now();
        if (!rooms.isEmpty()) {
            refreshJobItemMapper.batchInsert(
                rooms.stream().map(room -> buildJobItem(jobId, room.getId(), now)).toList()
            );
        }

        int updated = refreshJobMapper.markRunning(
            jobId,
            rooms.size(),
            now,
            rooms.isEmpty() ? "没有可刷新的房间" : "刷新任务执行中",
            now
        );
        if (updated == 0) {
            releaseActiveJob(jobId);
            activateNextManualJobIfIdle();
            return;
        }

        RefreshJob refreshJob = refreshJobMapper.selectByJobId(jobId);
        syncRuntimeState(refreshJob);

        if (rooms.isEmpty()) {
            finalizeJobIfNecessary(refreshJob);
            return;
        }

        for (Room room : rooms) {
            try {
                rabbitTemplate.convertAndSend(
                    refreshJobProperties.getQueueKey(),
                    JsonUtils.toJson(new RefreshRoomMessage(jobId, room.getId()))
                );
            } catch (Exception ex) {
                log.error("Failed to publish refresh room message, jobId={}, roomId={}", jobId, room.getId(), ex);
                markDispatchFailure(jobId, room.getId(), "刷新任务投递失败: " + sanitizeMessage(ex.getMessage()));
            }
        }
    }

    protected void markDispatchFailure(String jobId, Long roomId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        if (refreshJobItemMapper.claimPending(jobId, roomId, RefreshJobItemStatusEnum.RUNNING.name(), now) <= 0) {
            return;
        }
        RefreshJob refreshJob = updateJobItemOutcome(jobId, roomId, false, errorMessage);
        syncRuntimeState(refreshJob);
        finalizeJobIfNecessary(refreshJob);
    }

    private void finalizeJobIfNecessary(RefreshJob refreshJob) {
        if (refreshJob == null) {
            return;
        }
        if (!RefreshJobStatusEnum.RUNNING.name().equals(refreshJob.getStatus())) {
            return;
        }
        if (refreshJob.getCompletedRooms() == null || refreshJob.getTotalRooms() == null) {
            return;
        }
        if (!refreshJob.getCompletedRooms().equals(refreshJob.getTotalRooms())) {
            return;
        }

        String finalStatus = resolveFinalStatus(refreshJob);
        String summaryMessage = buildSummaryMessage(refreshJob);
        LocalDateTime finishedAt = LocalDateTime.now();
        int updated = refreshJobMapper.finishIfRunning(
            refreshJob.getJobId(),
            finalStatus,
            finishedAt,
            summaryMessage,
            finishedAt
        );
        if (updated == 0) {
            return;
        }

        RefreshJob finishedJob = refreshJobMapper.selectByJobId(refreshJob.getJobId());
        syncRuntimeState(finishedJob);
        releaseActiveJob(finishedJob.getJobId());
        activateNextManualJobIfIdle();
    }

    private String resolveFinalStatus(RefreshJob refreshJob) {
        int totalRooms = safeCount(refreshJob.getTotalRooms());
        int successRooms = safeCount(refreshJob.getSuccessRooms());
        int failedRooms = safeCount(refreshJob.getFailedRooms());

        if (totalRooms == 0 || failedRooms == 0) {
            return RefreshJobStatusEnum.SUCCESS.name();
        }
        if (successRooms == 0) {
            return RefreshJobStatusEnum.FAILED.name();
        }
        return RefreshJobStatusEnum.PARTIAL_SUCCESS.name();
    }

    private String buildSummaryMessage(RefreshJob refreshJob) {
        int totalRooms = safeCount(refreshJob.getTotalRooms());
        int successRooms = safeCount(refreshJob.getSuccessRooms());
        int failedRooms = safeCount(refreshJob.getFailedRooms());
        if (totalRooms == 0) {
            return "没有可刷新的房间";
        }
        if (failedRooms == 0) {
            return "刷新完成，全部 %d 个房间成功".formatted(totalRooms);
        }
        if (successRooms == 0) {
            return "刷新失败，%d 个房间全部失败".formatted(totalRooms);
        }
        return "刷新完成，成功 %d 个，失败 %d 个".formatted(successRooms, failedRooms);
    }

    private void activateNextManualJobIfIdle() {
        while (!hasActiveJob()) {
            String nextJobId = stringRedisTemplate.opsForList().leftPop(refreshJobProperties.getManualQueueKey());
            if (nextJobId == null || nextJobId.isBlank()) {
                return;
            }

            RefreshJob nextJob = refreshJobMapper.selectByJobId(nextJobId);
            if (nextJob == null || !RefreshJobStatusEnum.QUEUED.name().equals(nextJob.getStatus())) {
                continue;
            }

            if (!claimActiveJob(nextJobId)) {
                stringRedisTemplate.opsForList().leftPush(refreshJobProperties.getManualQueueKey(), nextJobId);
                return;
            }

            activateJobSafely(nextJobId);
            return;
        }
    }

    private void markJobFailed(String jobId, String message) {
        LocalDateTime now = LocalDateTime.now();
        refreshJobMapper.failIfPending(
            jobId,
            RefreshJobStatusEnum.FAILED.name(),
            now,
            sanitizeMessage(message),
            now
        );
        RefreshJob refreshJob = refreshJobMapper.selectByJobId(jobId);
        syncRuntimeState(refreshJob);
        releaseActiveJob(jobId);
        activateNextManualJobIfIdle();
    }

    private boolean claimActiveJob(String jobId) {
        Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
            refreshJobProperties.getActiveJobKey(),
            jobId,
            getRedisTtl()
        );
        return Boolean.TRUE.equals(claimed);
    }

    private boolean hasActiveJob() {
        Boolean hasKey = stringRedisTemplate.hasKey(refreshJobProperties.getActiveJobKey());
        return Boolean.TRUE.equals(hasKey);
    }

    private void releaseActiveJob(String jobId) {
        String currentJobId = stringRedisTemplate.opsForValue().get(refreshJobProperties.getActiveJobKey());
        if (jobId.equals(currentJobId)) {
            stringRedisTemplate.delete(refreshJobProperties.getActiveJobKey());
        }
    }

    private RefreshJobItem buildJobItem(String jobId, Long roomId, LocalDateTime createdAt) {
        RefreshJobItem item = new RefreshJobItem();
        item.setJobId(jobId);
        item.setRoomId(roomId);
        item.setStatus(RefreshJobItemStatusEnum.PENDING.name());
        item.setCreatedAt(createdAt);
        return item;
    }

    private void syncRuntimeState(RefreshJob refreshJob) {
        if (refreshJob == null) {
            return;
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("jobId", refreshJob.getJobId());
        payload.put("source", refreshJob.getSource());
        payload.put("status", refreshJob.getStatus());
        payload.put("totalRooms", String.valueOf(safeCount(refreshJob.getTotalRooms())));
        payload.put("completedRooms", String.valueOf(safeCount(refreshJob.getCompletedRooms())));
        payload.put("successRooms", String.valueOf(safeCount(refreshJob.getSuccessRooms())));
        payload.put("failedRooms", String.valueOf(safeCount(refreshJob.getFailedRooms())));
        payload.put("queuedAt", refreshJob.getQueuedAt() == null ? "" : refreshJob.getQueuedAt().toString());
        payload.put("startedAt", refreshJob.getStartedAt() == null ? "" : refreshJob.getStartedAt().toString());
        payload.put("finishedAt", refreshJob.getFinishedAt() == null ? "" : refreshJob.getFinishedAt().toString());
        payload.put("message", refreshJob.getMessage() == null ? "" : refreshJob.getMessage());

        String runtimeKey = buildRuntimeKey(refreshJob.getJobId());
        stringRedisTemplate.opsForHash().putAll(runtimeKey, payload);
        stringRedisTemplate.expire(runtimeKey, getRedisTtl());
    }

    private String buildRuntimeKey(String jobId) {
        return refreshJobProperties.getRuntimeKeyPrefix() + ":" + jobId;
    }

    private Duration getRedisTtl() {
        int ttlHours = refreshJobProperties.getRedisTtlHours() == null ? 24 : refreshJobProperties.getRedisTtlHours();
        return Duration.ofHours(ttlHours);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "系统繁忙，请稍后重试";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private RefreshJobVO toVO(RefreshJob refreshJob) {
        RefreshJobVO vo = new RefreshJobVO();
        vo.setJobId(refreshJob.getJobId());
        vo.setSource(refreshJob.getSource());
        vo.setStatus(refreshJob.getStatus());
        vo.setTotalRooms(safeCount(refreshJob.getTotalRooms()));
        vo.setCompletedRooms(safeCount(refreshJob.getCompletedRooms()));
        vo.setSuccessRooms(safeCount(refreshJob.getSuccessRooms()));
        vo.setFailedRooms(safeCount(refreshJob.getFailedRooms()));
        vo.setQueuedAt(refreshJob.getQueuedAt());
        vo.setStartedAt(refreshJob.getStartedAt());
        vo.setFinishedAt(refreshJob.getFinishedAt());
        vo.setMessage(refreshJob.getMessage());
        return vo;
    }
}
