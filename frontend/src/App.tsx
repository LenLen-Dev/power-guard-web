import { startTransition, useDeferredValue, useEffect, useMemo, useState } from "react";
import { AlertCircle, BatteryCharging, CalendarDays, Clock3, Github, Gift, House, Plus, RefreshCcw, Search, TriangleAlert, Trophy, Zap } from "lucide-react";
import { AnnouncementModal } from "./components/AnnouncementModal";
import { BuildingFilterCombobox } from "./components/BuildingFilterCombobox";
import { ConfirmDialog } from "./components/ConfirmDialog";
import { LotteryResultsDrawer } from "./components/LotteryResultsDrawer";
import { RoomCard } from "./components/RoomCard";
import { RoomDetailModal } from "./components/RoomDetailModal";
import { RoomFormModal } from "./components/RoomFormModal";
import { StatCard } from "./components/StatCard";
import { useLottery } from "./hooks/useLottery";
import { useRooms } from "./hooks/useRooms";
import { ApiError } from "./lib/api";
import { buildRoomName, type BuildingOption } from "./lib/buildings";
import { useRoomTrend } from "./hooks/useRoomTrend";
import { formatNumber } from "./lib/format";
import type { NoticeState, RefreshJob, RoomEditorValues, RoomFormValues, RoomStatus } from "./lib/types";

function isRefreshJobTerminal(refreshJob: RefreshJob | null) {
  return refreshJob?.status === "SUCCESS" || refreshJob?.status === "PARTIAL_SUCCESS" || refreshJob?.status === "FAILED";
}

function describeRefreshJobStatus(refreshJob: RefreshJob) {
  switch (refreshJob.status) {
    case "QUEUED":
      return "排队中";
    case "RUNNING":
      return "执行中";
    case "SUCCESS":
      return "已完成";
    case "PARTIAL_SUCCESS":
      return "部分完成";
    case "FAILED":
      return "已失败";
    default:
      return refreshJob.status;
  }
}

function describeRefreshJobSource(refreshJob: RefreshJob) {
  return refreshJob.source === "SCHEDULED" ? "统一查询任务" : "手动刷新任务";
}

function getNextScheduledFetchTime(now: Date) {
  const next = new Date(now);
  next.setSeconds(0, 0);
  if (now.getMinutes() < 30) {
    next.setMinutes(30);
    return next;
  }
  next.setHours(next.getHours() + 1);
  next.setMinutes(0);
  return next;
}

function getSecondsUntilNextScheduledFetch(now: Date) {
  const next = getNextScheduledFetchTime(now);
  return Math.max(0, Math.ceil((next.getTime() - now.getTime()) / 1000));
}

function getNextLotteryDrawTime(now: Date) {
  const candidates = [
    new Date(now.getFullYear(), now.getMonth(), 1, 12, 0, 0, 0),
    new Date(now.getFullYear(), now.getMonth(), 15, 12, 0, 0, 0),
    new Date(now.getFullYear(), now.getMonth() + 1, 1, 12, 0, 0, 0)
  ];

  return candidates.find((candidate) => candidate.getTime() >= now.getTime()) ?? candidates[candidates.length - 1];
}

function getSecondsUntilNextLotteryDraw(now: Date) {
  const next = getNextLotteryDrawTime(now);
  return Math.max(0, Math.ceil((next.getTime() - now.getTime()) / 1000));
}

function formatCountdown(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
  }
  return [minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}

function formatLotteryDrawTime(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const weekday = date.toLocaleDateString("zh-CN", { weekday: "short" });
  return `${month}月${day}日 ${weekday} 12:00`;
}

function buildLotteryWinnerKey(buildingName: string, roomId: string) {
  return `${buildingName}::${roomId}`;
}

function shouldPauseLiveCountdowns() {
  if (typeof window === "undefined" || typeof document === "undefined") {
    return false;
  }
  return window.matchMedia("(max-width: 767px)").matches && document.activeElement instanceof HTMLInputElement;
}

const ANNOUNCEMENT_SNOOZE_KEY = "powerguard:announcement:snooze-until";
const ANNOUNCEMENT_SNOOZE_DURATION_MS = 7 * 24 * 60 * 60 * 1000;
const GITHUB_REPOSITORY_URL = "https://github.com/LenLen-Dev/power-guard-web.git";

function App() {
  const {
    rooms,
    selectedRoomId,
    setSelectedRoomId,
    loading,
    manualRefreshing,
    refreshJob,
    refreshCooldownSeconds,
    submitting,
    error,
    triggerManualRefresh,
    createRoom,
    updateRoom,
    deleteRoom
  } = useRooms();
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<RoomStatus | null>(null);
  const [search, setSearch] = useState("");
  const [selectedBuilding, setSelectedBuilding] = useState<BuildingOption | null>(null);
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [announcementOpen, setAnnouncementOpen] = useState(false);
  const [lotteryDrawerOpen, setLotteryDrawerOpen] = useState(false);
  const [scheduledRefreshSeconds, setScheduledRefreshSeconds] = useState(() => getSecondsUntilNextScheduledFetch(new Date()));
  const [lotteryCountdownSeconds, setLotteryCountdownSeconds] = useState(() => getSecondsUntilNextLotteryDraw(new Date()));
  const [lastHandledRefreshJobId, setLastHandledRefreshJobId] = useState<string | null>(null);

  const deferredSearch = useDeferredValue(search);
  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;
  const { trend, loading: trendLoading, error: trendError } = useRoomTrend(selectedRoom?.id ?? null, 7);
  const { latestDraw, loading: lotteryLoading, error: lotteryError } = useLottery();
  const nextScheduledFetchTime = getNextScheduledFetchTime(new Date());
  const scheduledRefreshLabel = formatCountdown(scheduledRefreshSeconds);
  const nextLotteryDrawTime = getNextLotteryDrawTime(new Date());
  const lotteryCountdownLabel = formatCountdown(lotteryCountdownSeconds);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timer = window.setTimeout(() => setNotice(null), 3500);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    const updateCountdown = () => {
      if (shouldPauseLiveCountdowns()) {
        return;
      }
      const now = new Date();
      setScheduledRefreshSeconds(getSecondsUntilNextScheduledFetch(now));
      setLotteryCountdownSeconds(getSecondsUntilNextLotteryDraw(now));
    };

    updateCountdown();
    const timer = window.setInterval(updateCountdown, 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const snoozeUntil = window.localStorage.getItem(ANNOUNCEMENT_SNOOZE_KEY);
    if (snoozeUntil && Number(snoozeUntil) > Date.now()) {
      return;
    }
    setAnnouncementOpen(true);
  }, []);

  useEffect(() => {
    if (!refreshJob || !isRefreshJobTerminal(refreshJob) || lastHandledRefreshJobId === refreshJob.jobId) {
      return;
    }

    if (refreshJob.source === "SCHEDULED") {
      setLastHandledRefreshJobId(refreshJob.jobId);
      return;
    }

    if (refreshJob.status === "SUCCESS") {
      setNotice({ type: "success", message: refreshJob.message || "已完成一次全量电量查询" });
    } else if (refreshJob.status === "PARTIAL_SUCCESS") {
      setNotice({ type: "warning", message: refreshJob.message || "刷新已完成，但存在部分失败房间" });
    } else {
      setNotice({ type: "error", message: refreshJob.message || "刷新任务执行失败" });
    }

    setLastHandledRefreshJobId(refreshJob.jobId);
  }, [lastHandledRefreshJobId, refreshJob]);

  const buildingFilterOptions = useMemo(() => {
    const optionMap = new Map<string, BuildingOption>();
    for (const room of rooms) {
      if (!room.buildingId || !room.buildingName) {
        continue;
      }
      optionMap.set(room.buildingId, {
        id: room.buildingId,
        name: room.buildingName
      });
    }
    return Array.from(optionMap.values()).sort((left, right) => left.name.localeCompare(right.name, "zh-CN"));
  }, [rooms]);

  useEffect(() => {
    if (!selectedBuilding) {
      return;
    }
    if (buildingFilterOptions.some((option) => option.id === selectedBuilding.id)) {
      return;
    }
    setSelectedBuilding(null);
  }, [buildingFilterOptions, selectedBuilding]);

  const filteredRooms = rooms.filter((room) => {
    const keyword = deferredSearch.trim().toLowerCase();
    const matchesKeyword = !keyword || room.roomName.toLowerCase().includes(keyword);
    const matchesBuilding = !selectedBuilding || room.buildingId === selectedBuilding.id;
    return matchesKeyword && matchesBuilding;
  });

  const totalRooms = rooms.length;
  const alertRooms = rooms.filter((room) => room.status === 2).length;
  const warningRooms = rooms.filter((room) => room.status === 1).length;
  const totalRemain = rooms.reduce((sum, room) => sum + (room.remain ?? 0), 0);
  const latestWinnerMap = new Map(
    (latestDraw?.winners ?? []).map((winner) => [buildLotteryWinnerKey(winner.buildingName, winner.roomId), winner])
  );

  const buildingFilterLabel = selectedBuilding?.name ?? "";

  const totalSelectedBuildingRooms = selectedBuilding
    ? rooms.filter((room) => room.buildingId === selectedBuilding.id).length
    : null;

  const roomOverviewHint = selectedBuilding
    ? `当前楼栋共 ${totalSelectedBuildingRooms ?? 0} 个房间，已展示 ${filteredRooms.length} 个`
    : "当前所有已接入系统的房间";

  const handleBuildingFilterChange = (option: BuildingOption | null) => {
    setSelectedBuilding(option);
  };

  const handleCreate = async (values: RoomFormValues) => {
    const roomName = buildRoomName(values.buildingName, values.roomId) || values.roomName.trim();
    try {
      const created = await createRoom({
        buildingId: values.buildingId.trim(),
        buildingName: values.buildingName.trim(),
        roomId: values.roomId.trim(),
        roomName,
        alertEmail: values.alertEmail.trim() || undefined,
        threshold: Number(values.threshold)
      });
      setCreateOpen(false);
      setNotice({ type: "success", message: `已新增房间 ${created.roomName}` });
    } catch (requestError) {
      setNotice({ type: "error", message: requestError instanceof Error ? requestError.message : "创建房间失败" });
    }
  };

  const handleSave = async ({ buildingId, buildingName, roomId, roomName, threshold, alertEmail }: RoomEditorValues) => {
    if (!selectedRoom) {
      return;
    }
    try {
      await updateRoom(selectedRoom.id, {
        buildingId: buildingId.trim(),
        buildingName: buildingName.trim(),
        roomId: roomId.trim(),
        roomName: buildRoomName(buildingName, roomId) || roomName.trim(),
        alertEmail: alertEmail.trim() || undefined,
        threshold: Number(threshold)
      });
      setNotice({ type: "success", message: `已更新 ${buildRoomName(buildingName, roomId) || roomName.trim()} 的房间信息` });
    } catch (requestError) {
      setNotice({ type: "error", message: requestError instanceof Error ? requestError.message : "更新房间失败" });
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      const deletingSelected = deleteTarget.id === selectedRoomId;
      await deleteRoom(deleteTarget.id);
      setNotice({ type: "success", message: `已删除房间 ${deleteTarget.roomName}` });
      setDeleteTarget(null);
      if (deletingSelected) {
        setDetailOpen(false);
      }
    } catch (requestError) {
      setNotice({ type: "error", message: requestError instanceof Error ? requestError.message : "删除房间失败" });
    }
  };

  const handleOpenDetail = (roomId: number) => {
    setSelectedRoomId(roomId);
    setDetailOpen(true);
  };

  const handleManualRefresh = async () => {
    try {
      const nextJob = await triggerManualRefresh(selectedRoomId);
      if (!nextJob) {
        return;
      }
      setNotice({
        type: nextJob.status === "QUEUED" ? "warning" : "success",
        message: nextJob.status === "QUEUED" ? "刷新任务已提交，正在排队" : "刷新任务已提交，正在执行"
      });
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.code === 429) {
        setNotice({ type: "warning", message: requestError.message });
        return;
      }
      setNotice({ type: "error", message: requestError instanceof Error ? requestError.message : "刷新房间电量失败" });
    }
  };

  const handleCloseAnnouncement = () => {
    setAnnouncementOpen(false);
  };

  const handleDismissAnnouncementForWeek = () => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(
        ANNOUNCEMENT_SNOOZE_KEY,
        String(Date.now() + ANNOUNCEMENT_SNOOZE_DURATION_MS)
      );
    }
    setAnnouncementOpen(false);
  };

  const handleOpenLotteryDrawer = () => {
    setLotteryDrawerOpen(true);
  };

  const handleCloseLotteryDrawer = () => {
    setLotteryDrawerOpen(false);
  };

  return (
    <div className="app-shell bg-slate-50">
      <AnnouncementModal
        open={announcementOpen}
        onClose={handleCloseAnnouncement}
        onDismissForWeek={handleDismissAnnouncementForWeek}
      />
      <LotteryResultsDrawer
        open={lotteryDrawerOpen}
        draw={latestDraw}
        loading={lotteryLoading}
        error={lotteryError}
        onClose={handleCloseLotteryDrawer}
      />
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-[1320px] flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-[0_12px_24px_rgba(37,99,235,0.22)]">
              <Zap className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-[2rem] font-bold tracking-tight text-slate-900">电量监测系统</h1>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <a
              href={GITHUB_REPOSITORY_URL}
              target="_blank"
              rel="noreferrer"
              className="secondary-btn gap-2"
            >
              <Github className="h-4 w-4" />
              GitHub 仓库
            </a>
            <button
              type="button"
              className="secondary-btn gap-2"
              onClick={() => void handleManualRefresh()}
              disabled={manualRefreshing || refreshCooldownSeconds > 0}
            >
              <RefreshCcw className={`h-4 w-4 ${manualRefreshing ? "animate-spin" : ""}`} />
              {manualRefreshing
                ? "提交中..."
                : refreshJob?.status === "QUEUED"
                  ? "排队中..."
                  : refreshJob?.status === "RUNNING"
                    ? `刷新中 ${refreshJob.completedRooms}/${Math.max(refreshJob.totalRooms, 1)}`
                    : refreshCooldownSeconds > 0
                      ? `冷却中 ${refreshCooldownSeconds}s`
                      : "手动刷新"}
            </button>
            <button type="button" className="primary-btn gap-2" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              新增房间
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1320px] flex-col gap-5 px-4 py-6 sm:px-6">
        {notice ? (
          <div
            className={`flex items-center gap-3 rounded-2xl border px-4 py-3 text-sm shadow-sm ${
              notice.type === "success"
                ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                : notice.type === "warning"
                  ? "border-amber-200 bg-amber-50 text-amber-700"
                  : "border-rose-200 bg-rose-50 text-rose-700"
            }`}
          >
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{notice.message}</span>
          </div>
        ) : null}

        {error ? (
          <div className="flex items-center gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-sm">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        ) : null}

        {refreshJob ? (
          <section className="volatile-panel rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{describeRefreshJobSource(refreshJob)}</p>
                <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">{describeRefreshJobStatus(refreshJob)}</h2>
                <p className="mt-2 text-sm text-slate-500">{refreshJob.message || "正在同步任务进度"}</p>
              </div>
              <div className="grid gap-3 sm:grid-cols-4">
                <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-center">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">总房间</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{refreshJob.totalRooms}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-center">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">已完成</p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">{refreshJob.completedRooms}</p>
                </div>
                <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-center">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-emerald-600">成功</p>
                  <p className="mt-2 text-2xl font-bold text-emerald-600">{refreshJob.successRooms}</p>
                </div>
                <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-center">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-rose-600">失败</p>
                  <p className="mt-2 text-2xl font-bold text-rose-600">{refreshJob.failedRooms}</p>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        <section className="volatile-panel grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
          <article className="rounded-[26px] border border-sky-200 bg-[linear-gradient(135deg,_#f0f9ff,_#ffffff_60%,_#ecfeff)] p-5 shadow-sm">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-sky-100 bg-white text-sky-600 shadow-sm">
                <Clock3 className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-sky-600">统一查询</p>
                <p className="mt-3 text-3xl font-black tracking-tight text-slate-900">{scheduledRefreshLabel}</p>
                <div className="mt-3 inline-flex items-center gap-2 rounded-full bg-white/85 px-3 py-1 text-sm text-slate-600 shadow-sm">
                  <CalendarDays className="h-4 w-4 text-sky-500" />
                  下次 {nextScheduledFetchTime.toLocaleTimeString("zh-CN", {
                    hour: "2-digit",
                    minute: "2-digit",
                    hour12: false
                  })}
                </div>
              </div>
            </div>
          </article>

          <article className="rounded-[26px] border border-amber-200 bg-[linear-gradient(135deg,_#fff7ed,_#ffffff_60%,_#fffbeb)] p-5 shadow-sm">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-amber-100 bg-white text-amber-500 shadow-sm">
                <Gift className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-amber-500">抽奖开奖</p>
                <p className="mt-3 text-3xl font-black tracking-tight text-slate-900">{lotteryCountdownLabel}</p>
                <div className="mt-3 inline-flex items-center gap-2 rounded-full bg-white/85 px-3 py-1 text-sm text-slate-600 shadow-sm">
                  <CalendarDays className="h-4 w-4 text-amber-500" />
                  {formatLotteryDrawTime(nextLotteryDrawTime)}
                </div>
              </div>
            </div>
          </article>

          <button
            type="button"
            className="flex min-h-[138px] flex-col items-start justify-between rounded-[26px] border border-amber-200 bg-white px-5 py-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-[0_16px_28px_rgba(15,23,42,0.08)] lg:min-w-[240px]"
            onClick={handleOpenLotteryDrawer}
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-amber-100 bg-amber-50 text-amber-500 shadow-sm">
              <Trophy className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-amber-500">抽奖结果</p>
              <h3 className="mt-3 text-xl font-black tracking-tight text-slate-900">查看中奖名单</h3>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                {latestDraw
                  ? `${formatLotteryDrawTime(new Date(latestDraw.drawTime))} · 共 ${latestDraw.winnerCount} 个名额`
                  : "开奖后将在这里查看幸运宿舍"}
              </p>
            </div>
          </button>
        </section>

        <section className="grid gap-4 md:grid-cols-2">
          <StatCard title="监测房间" value={String(totalRooms)} hint="当前接入系统的房间数量" icon={<House className="h-5 w-5" />} tone="brand" />
          <StatCard
            title="总电量"
            value={`${formatNumber(totalRemain, 1, "0.0")} kWh`}
            hint="所有房间当前剩余电量总和"
            icon={<BatteryCharging className="h-5 w-5" />}
            tone="success"
          />
        </section>

        {alertRooms > 0 ? (
          <section className="overflow-hidden rounded-[28px] border border-rose-200 bg-gradient-to-r from-rose-50 via-white to-rose-50 shadow-sm">
            <div className="flex flex-col gap-5 px-5 py-5 lg:flex-row lg:items-center lg:justify-between lg:px-6">
              <div className="flex items-start gap-4">
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border border-rose-100 bg-white text-rose-500 shadow-sm">
                  <TriangleAlert className="h-7 w-7" />
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-rose-500">低电量告警</p>
                  <h3 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
                    检测到 <span className="text-rose-600">{alertRooms}</span> 个房间电量低于阈值
                  </h3>
                  <p className="mt-2 text-sm leading-6 text-slate-600">请优先处理这些房间，避免宿舍出现临时断电风险。</p>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-2xl border border-rose-200 bg-white px-5 py-4 text-center shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">当前告警</p>
                  <p className="mt-2 text-3xl font-bold text-rose-600">{alertRooms}</p>
                </div>
                <div className="rounded-2xl border border-amber-200 bg-white px-5 py-4 text-center shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">接近阈值</p>
                  <p className="mt-2 text-3xl font-bold text-amber-500">{warningRooms}</p>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h2 className="text-2xl font-bold tracking-tight text-slate-900">房间总览</h2>
              <p className="mt-2 text-sm text-slate-500">{roomOverviewHint}</p>
            </div>

            <div className="grid gap-3 lg:grid-cols-[minmax(0,260px)_minmax(0,280px)_auto] lg:items-center">
              <label className="relative min-w-[260px]">
                <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  className="field pl-11"
                  placeholder="搜索房间名称"
                  value={search}
                  onChange={(event) =>
                    startTransition(() => {
                      setSearch(event.target.value);
                    })
                  }
                />
              </label>
              <BuildingFilterCombobox
                options={buildingFilterOptions}
                valueId={selectedBuilding?.id ?? ""}
                valueName={buildingFilterLabel}
                onChange={handleBuildingFilterChange}
              />
              <span className="pill justify-center">展示 {filteredRooms.length} / {rooms.length}</span>
            </div>
          </div>

          {loading ? (
            <div className="panel-soft flex min-h-[320px] items-center justify-center text-slate-500">房间列表加载中...</div>
          ) : filteredRooms.length === 0 ? (
            <div className="panel-soft flex min-h-[320px] flex-col items-center justify-center text-center">
              <p className="text-lg font-semibold text-slate-900">未找到匹配房间</p>
              <p className="mt-3 max-w-sm text-sm leading-6 text-slate-500">试试调整搜索词，或者先新增一个房间。</p>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
              {filteredRooms.map((room) => (
                <RoomCard
                  key={room.id}
                  room={room}
                  lotteryWinner={latestWinnerMap.get(buildLotteryWinnerKey(room.buildingName, room.roomId)) ?? null}
                  selected={detailOpen && room.id === selectedRoomId}
                  onSelect={handleOpenDetail}
                  onDelete={setDeleteTarget}
                />
              ))}
            </div>
          )}

          <div className="mt-6 flex flex-wrap items-center justify-center gap-6 text-sm text-slate-500">
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-emerald-500" />
              <span>电量正常（高于阈值）</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-amber-500" />
              <span>电量偏低（接近阈值）</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-rose-500" />
              <span>电量告警（低于阈值）</span>
            </div>
          </div>
        </section>

      </main>

      <RoomFormModal open={createOpen} loading={submitting} onClose={() => setCreateOpen(false)} onSubmit={handleCreate} />
      <RoomDetailModal
        open={detailOpen}
        room={selectedRoom}
        trend={trend}
        trendLoading={trendLoading}
        trendError={trendError}
        submitting={submitting}
        onSave={handleSave}
        onClose={() => setDetailOpen(false)}
      />
      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="确认删除房间"
        description={
          deleteTarget
            ? `将删除房间 ${deleteTarget.roomName}。删除后房间及其全部历史耗电记录都会被移除。`
            : ""
        }
        confirmText="确认删除"
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => void handleDelete()}
        loading={submitting}
      />
    </div>
  );
}

export default App;
