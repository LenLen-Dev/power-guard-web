import { useEffect, useState } from "react";
import { roomApi } from "../lib/api";
import type { CreateRoomPayload, RefreshJob, RefreshJobStatus, RoomStatus, UpdateRoomPayload } from "../lib/types";

const TERMINAL_REFRESH_JOB_STATUSES: RefreshJobStatus[] = ["SUCCESS", "PARTIAL_SUCCESS", "FAILED"];
const LATEST_REFRESH_JOB_POLL_INTERVAL_MS = 5000;

function isTerminalRefreshJob(status: RefreshJobStatus) {
  return TERMINAL_REFRESH_JOB_STATUSES.includes(status);
}

function shouldReplaceTrackedRefreshJob(current: RefreshJob | null, candidate: RefreshJob | null) {
  if (!candidate) {
    return false;
  }
  if (!current) {
    return true;
  }
  if (candidate.jobId === current.jobId) {
    return false;
  }

  return new Date(candidate.queuedAt).getTime() >= new Date(current.queuedAt).getTime();
}

export function useRooms() {
  const [rooms, setRooms] = useState<RoomStatus[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [manualRefreshing, setManualRefreshing] = useState(false);
  const [refreshJob, setRefreshJob] = useState<RefreshJob | null>(null);
  const [refreshCooldownSeconds, setRefreshCooldownSeconds] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const applyRooms = (nextRooms: RoomStatus[], preferredRoomId?: number | null) => {
    setRooms(nextRooms);
    setSelectedRoomId((current) => {
      if (preferredRoomId && nextRooms.some((room) => room.id === preferredRoomId)) {
        return preferredRoomId;
      }
      if (current && nextRooms.some((room) => room.id === current)) {
        return current;
      }
      return nextRooms[0]?.id ?? null;
    });
  };

  const startRefreshCooldown = () => {
    setRefreshCooldownSeconds(10);
  };

  const refreshRooms = async (preferredRoomId?: number | null) => {
    const shouldShowLoading = rooms.length === 0 && loading;
    if (shouldShowLoading) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    setError(null);
    try {
      const nextRooms = await roomApi.listStatus();
      applyRooms(nextRooms, preferredRoomId);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "加载房间列表失败");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void refreshRooms();
  }, []);

  useEffect(() => {
    if (refreshCooldownSeconds <= 0) {
      return;
    }
    const timer = window.setInterval(() => {
      setRefreshCooldownSeconds((current) => (current <= 1 ? 0 : current - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [refreshCooldownSeconds]);

  useEffect(() => {
    if (!refreshJob || isTerminalRefreshJob(refreshJob.status)) {
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const nextJob = await roomApi.getRefreshJob(refreshJob.jobId);
        if (cancelled) {
          return;
        }
        setRefreshJob(nextJob);
        if (isTerminalRefreshJob(nextJob.status)) {
          await refreshRooms(selectedRoomId);
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : "获取刷新任务进度失败");
        }
      }
    }, 1000);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [refreshJob, selectedRoomId]);

  useEffect(() => {
    let cancelled = false;

    const syncLatestRefreshJob = async () => {
      try {
        const latestJob = await roomApi.getLatestRefreshJob();
        if (cancelled || !shouldReplaceTrackedRefreshJob(refreshJob, latestJob)) {
          return;
        }

        setRefreshJob(latestJob);
        if (latestJob && isTerminalRefreshJob(latestJob.status)) {
          await refreshRooms(selectedRoomId);
        }
      } catch {
        // Background sync of the latest refresh job should not interrupt the page.
      }
    };

    void syncLatestRefreshJob();
    const timer = window.setInterval(() => {
      void syncLatestRefreshJob();
    }, LATEST_REFRESH_JOB_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [refreshJob, selectedRoomId]);

  const createRoom = async (payload: CreateRoomPayload) => {
    setSubmitting(true);
    try {
      const created = await roomApi.create(payload);
      await refreshRooms(created.id);
      return created;
    } finally {
      setSubmitting(false);
    }
  };

  const updateRoom = async (id: number, payload: UpdateRoomPayload) => {
    setSubmitting(true);
    try {
      const updated = await roomApi.update(id, payload);
      await refreshRooms(updated.id);
      return updated;
    } finally {
      setSubmitting(false);
    }
  };

  const deleteRoom = async (id: number) => {
    setSubmitting(true);
    try {
      await roomApi.remove(id);
      await refreshRooms();
    } finally {
      setSubmitting(false);
    }
  };

  const triggerManualRefresh = async (preferredRoomId?: number | null) => {
    if (manualRefreshing || refreshCooldownSeconds > 0) {
      return refreshJob;
    }
    setManualRefreshing(true);
    setError(null);
    try {
      const nextJob = await roomApi.manualRefresh();
      setRefreshJob(nextJob);
      return nextJob;
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "执行手动刷新失败");
      throw requestError;
    } finally {
      setManualRefreshing(false);
      startRefreshCooldown();
    }
  };

  return {
    rooms,
    selectedRoomId,
    setSelectedRoomId,
    loading,
    refreshing,
    manualRefreshing,
    refreshJob,
    refreshCooldownSeconds,
    submitting,
    error,
    refreshRooms,
    triggerManualRefresh,
    createRoom,
    updateRoom,
    deleteRoom
  };
}
