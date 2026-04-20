import { AlertTriangle, Crown, Sparkles, Trash2, Zap } from "lucide-react";
import { calculateProgress, formatNumber } from "../lib/format";
import { getStatusMeta } from "../lib/status";
import type { LotteryWinner, RoomStatus } from "../lib/types";

interface RoomCardProps {
  room: RoomStatus;
  lotteryWinner?: LotteryWinner | null;
  selected: boolean;
  onSelect: (roomId: number) => void;
  onDelete: (room: RoomStatus) => void;
}

function formatReward(value: number) {
  return `${value.toFixed(value % 1 === 0 ? 0 : 1)} 元电费`;
}

export function RoomCard({ room, lotteryWinner, selected, onSelect, onDelete }: RoomCardProps) {
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
      } ${lotteryWinner ? "winner-room-card" : ""} cursor-pointer`}
    >
      <div className="flex h-full flex-col gap-4">
        {lotteryWinner ? (
          <div className="relative overflow-hidden rounded-[22px] border border-amber-200/80 bg-white/90 px-4 py-3 shadow-sm">
            <div className="absolute inset-y-0 right-0 w-24 bg-[radial-gradient(circle_at_center,_rgba(251,191,36,0.2),_transparent_70%)]" />
            <div className="relative flex items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-amber-600">
                  <Crown className="h-4 w-4 shrink-0" />
                  <span className="text-xs font-black uppercase tracking-[0.18em]">本期中奖</span>
                  <span className="rounded-full bg-amber-500 px-2 py-0.5 text-[11px] font-black text-white">
                    NO.{lotteryWinner.winnerRank}
                  </span>
                </div>
                <p className="mt-2 text-sm font-semibold text-slate-700">好运已经落到这间宿舍啦</p>
              </div>
              <div className="shrink-0 text-right">
                <div className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-3 py-1 text-emerald-600">
                  <Sparkles className="h-3.5 w-3.5" />
                  <span className="text-xs font-black">{formatReward(lotteryWinner.rewardAmount)}</span>
                </div>
              </div>
            </div>
          </div>
        ) : null}

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
