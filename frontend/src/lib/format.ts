export function formatNumber(value?: number | null, digits = 2, fallback = "--") {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return fallback;
  }
  return Number(value).toFixed(digits);
}

export function formatDate(value?: string | null) {
  if (!value) {
    return "--";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit"
  }).format(new Date(value));
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return "暂无";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

export function calculateProgress(remain?: number | null, total?: number | null, threshold?: number | null) {
  const safeRemain = remain ?? 0;
  const base = total && total > 0 ? total : Math.max(safeRemain, (threshold ?? 0) + 20, 50);
  return Math.max(0, Math.min(100, (safeRemain / base) * 100));
}
