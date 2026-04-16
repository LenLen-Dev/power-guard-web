import type { ReactNode } from "react";

interface StatCardProps {
  title: string;
  value: string;
  hint: string;
  icon: ReactNode;
  tone?: "default" | "brand" | "success" | "danger";
}

const toneClassMap = {
  default: {
    card: "border-slate-200 bg-white",
    icon: "border-slate-200 bg-slate-50 text-slate-500",
    value: "text-slate-900"
  },
  brand: {
    card: "border-blue-200 bg-white",
    icon: "border-blue-100 bg-blue-50 text-blue-600",
    value: "text-slate-900"
  },
  success: {
    card: "border-emerald-200 bg-white",
    icon: "border-emerald-100 bg-emerald-50 text-emerald-600",
    value: "text-slate-900"
  },
  danger: {
    card: "border-rose-200 bg-rose-50/70",
    icon: "border-rose-100 bg-rose-100 text-rose-600",
    value: "text-rose-600"
  }
} as const;

export function StatCard({ title, value, hint, icon, tone = "default" }: StatCardProps) {
  const toneClass = toneClassMap[tone];

  return (
    <div className={`relative flex flex-col items-center overflow-hidden rounded-2xl border p-5 text-center shadow-sm ${toneClass.card}`}>
      <div className="flex items-center justify-center gap-2">
        <div className={`rounded-xl border p-2 ${toneClass.icon}`}>{icon}</div>
        <span className="text-sm font-semibold text-slate-500">{title}</span>
      </div>
      <div className="mt-4 space-y-2">
        <div className={`text-4xl font-semibold tracking-tight ${toneClass.value}`}>{value}</div>
        <p className="text-sm text-slate-500">{hint}</p>
      </div>
    </div>
  );
}
