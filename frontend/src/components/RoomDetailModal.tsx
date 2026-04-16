import { X } from "lucide-react";
import type { DailyTrend, RoomEditorValues, RoomStatus } from "../lib/types";
import { RoomDetailPanel } from "./RoomDetailPanel";

interface RoomDetailModalProps {
  open: boolean;
  room: RoomStatus | null;
  trend: DailyTrend[];
  trendLoading: boolean;
  trendError: string | null;
  submitting?: boolean;
  onSave: (payload: RoomEditorValues) => Promise<void>;
  onClose: () => void;
}

export function RoomDetailModal({
  open,
  room,
  trend,
  trendLoading,
  trendError,
  submitting,
  onSave,
  onClose
}: RoomDetailModalProps) {
  if (!open || !room) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/30 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="mx-auto flex max-h-[92vh] w-full max-w-5xl flex-col" onClick={(event) => event.stopPropagation()}>
        <div className="mb-3 flex justify-end">
          <button
            type="button"
            className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-500 shadow-sm transition hover:border-slate-300 hover:text-slate-700"
            onClick={onClose}
            aria-label="关闭房间详情弹窗"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="custom-scrollbar flex-1 overflow-y-auto pr-1">
          <RoomDetailPanel
            room={room}
            trend={trend}
            trendLoading={trendLoading}
            trendError={trendError}
            submitting={submitting}
            onSave={onSave}
            mode="full"
          />
        </div>
      </div>
    </div>
  );
}
