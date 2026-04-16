import { useEffect, useState } from "react";
import { AlertTriangle, Building2, Mail, Save, Zap } from "lucide-react";
import { BuildingCombobox } from "./BuildingCombobox";
import { BUILDING_OPTIONS, buildRoomName, findBuildingById } from "../lib/buildings";
import { formatDateTime, formatNumber } from "../lib/format";
import type { DailyTrend, RoomEditorValues, RoomStatus } from "../lib/types";
import { StatusBadge } from "./StatusBadge";
import { TrendChart } from "./TrendChart";

interface RoomDetailPanelProps {
  room: RoomStatus | null;
  trend: DailyTrend[];
  trendLoading: boolean;
  trendError: string | null;
  submitting?: boolean;
  onSave: (payload: RoomEditorValues) => Promise<void>;
  mode?: "full" | "chart-only";
}

export function RoomDetailPanel({
  room,
  trend,
  trendLoading,
  trendError,
  submitting,
  onSave,
  mode = "full"
}: RoomDetailPanelProps) {
  const [buildingId, setBuildingId] = useState("");
  const [buildingName, setBuildingName] = useState("");
  const [roomId, setRoomId] = useState("");
  const [threshold, setThreshold] = useState("");
  const [alertEmail, setAlertEmail] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    const building = room ? findBuildingById(room.buildingId) : null;
    setBuildingId(room?.buildingId ?? "");
    setBuildingName(building?.name ?? room?.buildingName ?? "");
    setRoomId(room?.roomId ?? "");
    setThreshold(room?.threshold?.toString() ?? "");
    setAlertEmail(room?.alertEmail ?? "");
    setSaveError(null);
  }, [room]);

  const previewRoomName = buildRoomName(buildingName, roomId) || room?.roomName || "";

  if (!room) {
    return (
      <section className={`${mode === "chart-only" ? "min-h-[380px]" : "panel min-h-[640px]"} flex items-center justify-center p-8`}>
        <div className="max-w-md text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-500">
            {mode === "chart-only" ? "Trend Overview" : "Room Detail"}
          </p>
          <h2 className="mt-4 text-3xl font-semibold text-slate-900">选择一个房间查看详情</h2>
          <p className="mt-4 leading-7 text-slate-500">
            左侧卡片支持滚动与搜索。选中房间后，这里会显示实时剩余电量、告警信息以及最近 7 天的用电趋势图。
          </p>
        </div>
      </section>
    );
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!buildingId.trim() || !buildingName.trim()) {
      setSaveError("请选择楼栋");
      return;
    }
    if (!roomId.trim()) {
      setSaveError("请输入房间号");
      return;
    }
    if (!threshold.trim() || Number(threshold) < 0) {
      setSaveError("请输入大于等于 0 的告警阈值");
      return;
    }
    if (alertEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(alertEmail.trim())) {
      setSaveError("请输入有效的告警邮箱");
      return;
    }
    setSaveError(null);
    await onSave({
      buildingId,
      buildingName,
      roomId,
      roomName: previewRoomName,
      threshold,
      alertEmail
    });
  };

  if (mode === "chart-only") {
    return (
      <div>
        {trendLoading ? (
          <div className="panel-soft flex h-[360px] items-center justify-center text-slate-500">趋势加载中...</div>
        ) : trendError ? (
          <div className="panel-soft flex h-[360px] items-center justify-center text-rose-600">{trendError}</div>
        ) : trend.length === 0 ? (
          <div className="panel-soft flex h-[360px] items-center justify-center text-slate-500">暂无趋势数据</div>
        ) : (
          <TrendChart data={trend} />
        )}
      </div>
    );
  }

  return (
    <section className="panel min-h-[640px] p-6 lg:p-8">
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500">Room Detail</p>
              <StatusBadge status={room.status} text={room.statusDesc} />
            </div>
            <h2 className="mt-3 text-3xl font-semibold text-slate-900">{previewRoomName}</h2>
            <p className="mt-2 flex items-center gap-2 text-sm text-slate-500">
              <Building2 className="h-4 w-4" />
              {buildingName} · 房间号 {roomId}
            </p>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-500">剩余电量</p>
              <p className="mt-3 text-2xl font-semibold text-slate-900">{formatNumber(room.remain)}%</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-500">告警阈值</p>
              <p className="mt-3 text-2xl font-semibold text-slate-900">{formatNumber(room.threshold)} kWh</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-500">最近更新</p>
              <p className="mt-3 text-sm font-medium text-slate-700">{formatDateTime(room.updateTime)}</p>
            </div>
          </div>
        </div>

        <div className="grid gap-5">
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="rounded-[26px] border border-slate-200 bg-slate-50/50 p-5">
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">房间详情与告警设置</h3>
                </div>
                <span className="pill bg-slate-900 text-white">低电量告警</span>
              </div>

              <div className="space-y-4">
                <BuildingCombobox
                  label="楼栋选择"
                  options={BUILDING_OPTIONS}
                  valueId={buildingId}
                  valueName={buildingName}
                  onChange={(option) => {
                    setBuildingId(option.id);
                    setBuildingName(option.name);
                    setSaveError(null);
                  }}
                />

                <label>
                  <span className="field-label">房间号（roomId）</span>
                  <div className="relative">
                    <Building2 className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                      type="text"
                      className="field pl-11"
                      placeholder="例如：215"
                      value={roomId}
                      onChange={(event) => {
                        setRoomId(event.target.value);
                        setSaveError(null);
                      }}
                    />
                  </div>
                </label>

                <label>
                  <span className="field-label">房间名称（自动生成）</span>
                  <input type="text" className="field bg-white" value={previewRoomName} readOnly placeholder="将根据楼栋和房间号自动生成" />
                </label>

                <label>
                  <span className="field-label">告警邮箱</span>
                  <div className="relative">
                    <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                      type="email"
                      className="field pl-11"
                      placeholder="未配置时可留空"
                      value={alertEmail}
                      onChange={(event) => setAlertEmail(event.target.value)}
                    />
                  </div>
                </label>

                <label>
                  <span className="field-label">告警阈值 (kWh)</span>
                  <div className="relative">
                    <AlertTriangle className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <input
                      type="number"
                      className="field pl-11"
                      placeholder="请输入告警阈值"
                      value={threshold}
                      onChange={(event) => setThreshold(event.target.value)}
                    />
                  </div>
                </label>

                <div className="rounded-3xl border border-slate-200 bg-white p-4 text-sm leading-7 text-slate-500">
                  <p>当前状态：{room.statusDesc}</p>
                  <p>房间名称会按“楼栋名称-房间号”自动生成并同步到后端。</p>
                </div>

                {saveError ? <div className="text-sm text-rose-600">{saveError}</div> : null}
              </div>
            </div>

            <div className="flex justify-end">
              <button type="submit" className="primary-btn gap-2" disabled={submitting}>
                <Save className="h-4 w-4" />
                {submitting ? "保存中..." : "保存配置"}
              </button>
            </div>
          </form>

          <div className="rounded-[26px] border border-slate-200 bg-slate-50/50 p-5">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-semibold text-slate-900">最近用电统计图</h3>
                <p className="mt-1 text-sm text-slate-500">用于查看该房间近 7 天用电变化。</p>
              </div>
              <span className="pill">
                <Zap className="h-4 w-4" />
                最近 7 天
              </span>
            </div>

            {trendLoading ? (
              <div className="panel-soft flex h-[240px] items-center justify-center text-slate-500">趋势加载中...</div>
            ) : trendError ? (
              <div className="panel-soft flex h-[240px] items-center justify-center text-rose-600">{trendError}</div>
            ) : trend.length === 0 ? (
              <div className="panel-soft flex h-[240px] items-center justify-center text-slate-500">暂无趋势数据</div>
            ) : (
              <TrendChart data={trend} compact />
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
