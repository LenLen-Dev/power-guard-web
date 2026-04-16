export function getStatusMeta(status: number) {
  if (status === 2) {
    return {
      label: "告警",
      accent: "text-rose-600",
      chipClass: "border-rose-200 bg-rose-50 text-rose-600",
      barClass: "from-rose-500 to-rose-400",
      glowClass: "shadow-[0_0_0_1px_rgba(244,63,94,0.14)]"
    };
  }

  if (status === 1) {
    return {
      label: "警告",
      accent: "text-amber-600",
      chipClass: "border-amber-200 bg-amber-50 text-amber-700",
      barClass: "from-amber-500 to-orange-400",
      glowClass: "shadow-[0_0_0_1px_rgba(251,191,36,0.14)]"
    };
  }

  return {
    label: "正常",
    accent: "text-emerald-600",
    chipClass: "border-emerald-200 bg-emerald-50 text-emerald-700",
    barClass: "from-emerald-500 to-emerald-400",
    glowClass: "shadow-[0_0_0_1px_rgba(45,212,191,0.12)]"
  };
}
