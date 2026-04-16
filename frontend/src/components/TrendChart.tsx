import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import { formatDate, formatNumber } from "../lib/format";
import type { DailyTrend } from "../lib/types";

interface TrendChartProps {
  data: DailyTrend[];
  compact?: boolean;
}

export function TrendChart({ data, compact = false }: TrendChartProps) {
  const chartData = data.map((item) => ({
    ...item,
    label: formatDate(item.date),
    consumptionValue: item.consumption ?? undefined,
    endRemainValue: item.endRemain ?? undefined
  }));

  return (
    <div className={`rounded-[24px] border border-slate-200 bg-white ${compact ? "h-[240px] p-3" : "h-[340px] p-4"}`}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData} margin={{ top: 20, right: 12, left: -18, bottom: 0 }}>
          <defs>
            <linearGradient id="consumptionFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#334155" stopOpacity={0.22} />
              <stop offset="100%" stopColor="#334155" stopOpacity={0.03} />
            </linearGradient>
            <linearGradient id="remainFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#94a3b8" stopOpacity={0.28} />
              <stop offset="100%" stopColor="#94a3b8" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="rgba(148,163,184,0.24)" strokeDasharray="4 4" vertical={false} />
          <XAxis dataKey="label" stroke="#94a3b8" tickLine={false} axisLine={false} fontSize={12} />
          <YAxis stroke="#94a3b8" tickLine={false} axisLine={false} width={38} fontSize={12} />
          <Tooltip
            cursor={{ stroke: "rgba(148,163,184,0.4)", strokeWidth: 1 }}
            contentStyle={{
              borderRadius: 18,
              border: "1px solid rgba(226, 232, 240, 1)",
              background: "rgba(255, 255, 255, 0.98)",
              boxShadow: "0 18px 48px rgba(15, 23, 42, 0.12)"
            }}
            formatter={(value, key) => {
              const numericValue = normalizeTooltipValue(value);
              if (key === "consumptionValue") {
                return [`${formatNumber(numericValue, 2)} kWh`, "日耗电量"];
              }
              if (key === "endRemainValue") {
                return [`${formatNumber(numericValue, 2)} kWh`, "日末剩余电量"];
              }
              return [value, key];
            }}
            labelFormatter={(_, payload) => {
              const entry = payload?.[0]?.payload as DailyTrend | undefined;
              return entry ? `${formatDate(entry.date)}${entry.rechargeDetected ? " · 检测到充值" : ""}` : "";
            }}
          />
          <Area
            type="monotone"
            dataKey="consumptionValue"
            name="日耗电量"
            stroke="#334155"
            fill="url(#consumptionFill)"
            strokeWidth={2.4}
            activeDot={{ r: 4, fill: "#334155" }}
          />
          <Line
            type="monotone"
            dataKey="endRemainValue"
            name="日末剩余电量"
            stroke="#64748b"
            strokeWidth={1.8}
            dot={{ r: 2.5, strokeWidth: 0, fill: "#64748b" }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

function normalizeTooltipValue(value: string | number | Array<string | number>) {
  const candidate = Array.isArray(value) ? value[0] : value;
  const numericValue = Number(candidate);
  return Number.isNaN(numericValue) ? null : numericValue;
}
