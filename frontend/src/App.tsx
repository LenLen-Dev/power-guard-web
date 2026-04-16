import { startTransition, useDeferredValue, useEffect, useState } from "react";
import { AlertCircle, BatteryCharging, Clock3, House, Plus, RefreshCcw, Search, TriangleAlert, Zap } from "lucide-react";
import { ConfirmDialog } from "./components/ConfirmDialog";
import { RoomCard } from "./components/RoomCard";
import { RoomDetailModal } from "./components/RoomDetailModal";
import { RoomFormModal } from "./components/RoomFormModal";
import { StatCard } from "./components/StatCard";
import { useRooms } from "./hooks/useRooms";
import { ApiError } from "./lib/api";
import { buildRoomName } from "./lib/buildings";
import { useRoomTrend } from "./hooks/useRoomTrend";
import { formatNumber } from "./lib/format";
import type { NoticeState, RoomEditorValues, RoomFormValues, RoomStatus } from "./lib/types";

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

function formatCountdown(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
  }
  return [minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}

function App() {
  const {
    rooms,
    selectedRoomId,
    setSelectedRoomId,
    loading,
    manualRefreshing,
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
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const [scheduledRefreshSeconds, setScheduledRefreshSeconds] = useState(() => getSecondsUntilNextScheduledFetch(new Date()));

  const deferredSearch = useDeferredValue(search);
  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? null;
  const { trend, loading: trendLoading, error: trendError } = useRoomTrend(selectedRoom?.id ?? null, 7);
  const nextScheduledFetchTime = getNextScheduledFetchTime(new Date());
  const scheduledRefreshLabel = formatCountdown(scheduledRefreshSeconds);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timer = window.setTimeout(() => setNotice(null), 3500);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    const updateCountdown = () => {
      setScheduledRefreshSeconds(getSecondsUntilNextScheduledFetch(new Date()));
    };

    updateCountdown();
    const timer = window.setInterval(updateCountdown, 1000);
    return () => window.clearInterval(timer);
  }, []);

  const filteredRooms = rooms.filter((room) => {
    const keyword = deferredSearch.trim().toLowerCase();
    if (!keyword) {
      return true;
    }
    return room.roomName.toLowerCase().includes(keyword);
  });

  const totalRooms = rooms.length;
  const alertRooms = rooms.filter((room) => room.status === 2).length;
  const warningRooms = rooms.filter((room) => room.status === 1).length;
  const totalRemain = rooms.reduce((sum, room) => sum + (room.remain ?? 0), 0);

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
      await triggerManualRefresh(selectedRoomId);
      setNotice({ type: "success", message: "已执行一次全量电量查询" });
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.code === 429) {
        setNotice({ type: "warning", message: requestError.message });
        return;
      }
      setNotice({ type: "error", message: requestError instanceof Error ? requestError.message : "刷新房间电量失败" });
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
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
            <button
              type="button"
              className="secondary-btn gap-2"
              onClick={() => void handleManualRefresh()}
              disabled={manualRefreshing || refreshCooldownSeconds > 0}
            >
              <RefreshCcw className={`h-4 w-4 ${manualRefreshing ? "animate-spin" : ""}`} />
              {manualRefreshing ? "刷新中..." : refreshCooldownSeconds > 0 ? `冷却中 ${refreshCooldownSeconds}s` : "自动刷新"}
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

        <section className="overflow-hidden rounded-[28px] border border-sky-200 bg-gradient-to-r from-sky-50 via-white to-cyan-50 shadow-sm">
          <div className="flex flex-col gap-4 px-5 py-5 lg:flex-row lg:items-center lg:justify-between lg:px-6">
            <div className="flex items-start gap-4">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border border-sky-100 bg-white text-sky-600 shadow-sm">
                <Clock3 className="h-7 w-7" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-sky-600">统一查询倒计时</p>
                <h3 className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
                  距下次统一查询还有 <span className="text-sky-600">{scheduledRefreshLabel}</span>
                </h3>
              </div>
            </div>

            <div className="rounded-2xl border border-sky-200 bg-white px-5 py-4 text-center shadow-sm">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">下一次统一查询</p>
              <p className="mt-2 text-3xl font-bold text-sky-600">
                {nextScheduledFetchTime.toLocaleTimeString("zh-CN", {
                  hour: "2-digit",
                  minute: "2-digit",
                  hour12: false
                })}
              </p>
            </div>
          </div>
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
              
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
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
