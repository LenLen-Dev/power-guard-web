import { getStatusMeta } from "../lib/status";

interface StatusBadgeProps {
  status: number;
  text?: string;
}

export function StatusBadge({ status, text }: StatusBadgeProps) {
  const meta = getStatusMeta(status);
  return <span className={`pill border ${meta.chipClass}`}>{text ?? meta.label}</span>;
}
