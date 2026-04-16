interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmText: string;
  onCancel: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmText,
  onCancel,
  onConfirm,
  loading
}: ConfirmDialogProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-[28px] border border-slate-200 bg-white p-6 shadow-[0_30px_80px_rgba(15,23,42,0.16)]">
        <div className="space-y-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-rose-50 text-lg text-rose-500">!</div>
          <h3 className="text-xl font-semibold text-slate-900">{title}</h3>
          <p className="text-sm leading-6 text-slate-500">{description}</p>
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" className="secondary-btn" onClick={onCancel} disabled={loading}>
            取消
          </button>
          <button type="button" className="danger-btn" onClick={onConfirm} disabled={loading}>
            {loading ? "处理中..." : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
