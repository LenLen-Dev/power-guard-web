import { AlertTriangle, Trash2, Zap } from "lucide-react";
import { calculateProgress, formatNumber } from "../lib/format";
import { getStatusMeta } from "../lib/status";
import type { RoomStatus } from "../lib/types";

interface RoomCardProps {
  room: RoomStatus;
  selected: boolean;
  onSelect: (roomId: number) => void;
  onDelete: (room: RoomStatus) => void;
}

export function RoomCard({ room, selected, onSelect, onDelete }: RoomCardProps) {
  const meta = getStatusMeta(room.status);
  const progress = calculateProgress(room.remain, room.total, room.threshold);
  const total = room.total && room.total > 0 ? room.total : Math.max((room.remain ?? 0) + (room.threshold ?? 0), 100);
  const statusText = room.status === 2 ? "电量不足" : room.status === 1 ? "接近阈值" : "状态正常";

  const toneClass =
    room.status === 2
      ? {
          card: "border-rose-400 bg-rose-50/50",
          icon: "bg-rose-100 text-rose-600",
          bar: "bg-rose-500",
          value: "text-rose-600",
          hint: "text-rose-500"
        }
      : room.status === 1
        ? {
            card: "border-amber-400 bg-amber-50/50",
            icon: "bg-amber-100 text-amber-600",
            bar: "bg-amber-500",
            value: "text-amber-600",
            hint: "text-amber-500"
          }
        : {
            card: "border-emerald-400 bg-emerald-50/40",
            icon: "bg-emerald-100 text-emerald-600",
            bar: "bg-emerald-500",
            value: "text-emerald-600",
            hint: "text-emerald-500"
          };

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onSelect(room.id)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect(room.id);
        }
      }}
      className={`h-full rounded-[26px] border p-4 text-left transition hover:-translate-y-0.5 hover:shadow-[0_16px_28px_rgba(15,23,42,0.08)] ${
        selected ? "border-blue-500 bg-white shadow-[0_0_0_3px_rgba(37,99,235,0.12)]" : toneClass.card
      } cursor-pointer`}
    >
      <div className="flex h-full flex-col gap-4">
        <div className="space-y-2">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-start gap-3">
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl ${toneClass.icon}`}>
                <Zap className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className="line-clamp-1 break-words text-[1.35rem] font-semibold leading-tight text-slate-900">{room.roomId}</p>
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-2">
              <span className={`rounded-full px-3 py-1 text-xs font-semibold ${toneClass.icon}`}>{statusText}</span>
              <button
                type="button"
                className="rounded-2xl border border-slate-200 bg-white/90 p-2.5 text-slate-400 transition hover:border-rose-200 hover:bg-rose-50 hover:text-rose-500"
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete(room);
                }}
                aria-label={`删除房间 ${room.roomId}`}
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          </div>

          <p className="pl-[3.25rem] text-[13px] text-slate-500 whitespace-nowrap">{room.alertEmail || "未配置告警邮箱"}</p>
        </div>

        <div className="flex items-end justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-end gap-2">
              <span className={`text-[2.15rem] font-bold tracking-tight ${toneClass.value}`}>{formatNumber(room.remain, 1, "0.0")}</span>
              <span className="pb-1 text-sm font-semibold text-slate-500">/ {formatNumber(total, 0, "0")} kWh</span>
            </div>
            <p className="mt-1 text-sm text-slate-500">当前剩余电量</p>
          </div>

          <div className="shrink-0 text-right">
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-slate-400">阈值</p>
            <p className="mt-1 text-lg font-semibold text-slate-900">{formatNumber(room.threshold, 0, "0")} kWh</p>
          </div>
        </div>

        <div className="h-2.5 overflow-hidden rounded-full bg-slate-200/90">
          <div className={`h-full rounded-full ${toneClass.bar}`} style={{ width: `${progress}%` }} />
        </div>

        <div className="flex items-center justify-between gap-4 text-sm">
          <div className="flex min-w-0 items-center gap-2">
            {room.status !== 0 ? <AlertTriangle className={`h-4 w-4 shrink-0 ${toneClass.hint}`} /> : null}
            <span className={room.status !== 0 ? toneClass.hint : "text-slate-500"}>{statusText}</span>
          </div>
          <span className={`font-semibold ${meta.accent}`}>{formatNumber(progress, 1, "0.0")}%</span>
        </div>

        <div className="flex items-center justify-between gap-4 border-t border-slate-200/80 pt-3 text-sm text-slate-500">
          <span className="truncate">{room.buildingName}</span>
          <span className="shrink-0 text-xs font-semibold text-slate-400">ID #{room.id}</span>
        </div>
      </div>
    </div>
  );
}
