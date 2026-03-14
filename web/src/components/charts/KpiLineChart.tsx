import React from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";

export interface KpiSeriesConfig {
  key: string;
  label: string;
  color?: string;
  dashed?: boolean;
}

interface KpiLineChartProps {
  data: Record<string, unknown>[];
  series: KpiSeriesConfig[];
  xKey?: string;
  xFormatter?: (v: unknown) => string;
  yFormatter?: (v: number) => string;
  height?: number;
}

const CustomTooltip = ({
  active,
  payload,
  label,
  yFormatter,
}: {
  active?: boolean;
  payload?: Array<{ name: string; value: number; color: string }>;
  label?: string;
  yFormatter: (v: number) => string;
}) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="glass-card px-3 py-2 text-xs space-y-1 min-w-[120px]">
      <p className="text-text-secondary mb-1">{label}</p>
      {payload.map((entry) => (
        <div key={entry.name} className="flex items-center gap-2">
          <span
            className="inline-block w-2 h-2 rounded-full"
            style={{ background: entry.color }}
          />
          <span className="text-text-secondary">{entry.name}:</span>
          <span className="text-white font-medium ml-auto">
            {yFormatter(entry.value)}
          </span>
        </div>
      ))}
    </div>
  );
};

const DEFAULT_COLORS = ["#6366f1", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6"];

export const KpiLineChart: React.FC<KpiLineChartProps> = ({
  data,
  series,
  xKey = "date",
  xFormatter,
  yFormatter = String,
  height = 240,
}) => {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 4, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid
          strokeDasharray="3 3"
          stroke="rgba(255,255,255,0.04)"
          vertical={false}
        />
        <XAxis
          dataKey={xKey}
          tickFormatter={xFormatter}
          tick={{ fill: "#9ca3af", fontSize: 11 }}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          tickFormatter={yFormatter}
          tick={{ fill: "#9ca3af", fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          width={56}
        />
        <Tooltip
          content={<CustomTooltip yFormatter={yFormatter} />}
          cursor={{ stroke: "rgba(255,255,255,0.08)" }}
        />
        {series.length > 1 && (
          <Legend
            wrapperStyle={{ fontSize: 11, color: "#9ca3af", paddingTop: 8 }}
          />
        )}
        {series.map((s, i) => (
          <Line
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.label}
            stroke={s.color ?? DEFAULT_COLORS[i % DEFAULT_COLORS.length]}
            strokeWidth={2}
            strokeDasharray={s.dashed ? "4 4" : undefined}
            dot={false}
            activeDot={{ r: 4 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
};
