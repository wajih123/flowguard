import React from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";

interface SpendingItem {
  category: string;
  amount: number;
  color?: string;
}

interface SpendingChartProps {
  data: SpendingItem[];
  currency?: string;
}

const DEFAULT_COLORS = [
  "#6366f1",
  "#8b5cf6",
  "#06b6d4",
  "#10b981",
  "#f59e0b",
  "#ef4444",
];

const CustomTooltip = ({
  active,
  payload,
  currency,
}: {
  active?: boolean;
  payload?: Array<{ value: number; payload: SpendingItem }>;
  currency: string;
}) => {
  if (!active || !payload?.length) return null;
  const item = payload[0];
  return (
    <div className="glass-card px-3 py-2 text-xs">
      <p className="text-text-secondary mb-0.5">{item.payload.category}</p>
      <p className="text-white font-semibold">
        {new Intl.NumberFormat("fr-FR", {
          style: "currency",
          currency,
          maximumFractionDigits: 0,
        }).format(item.value)}
      </p>
    </div>
  );
};

export const SpendingChart: React.FC<SpendingChartProps> = ({
  data,
  currency = "EUR",
}) => {
  const sorted = [...data].sort((a, b) => b.amount - a.amount).slice(0, 6);

  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart
        data={sorted}
        layout="vertical"
        margin={{ top: 0, right: 16, bottom: 0, left: 0 }}
      >
        <XAxis
          type="number"
          hide
          tickFormatter={(v) =>
            new Intl.NumberFormat("fr-FR", {
              notation: "compact",
              currency,
            }).format(v)
          }
        />
        <YAxis
          type="category"
          dataKey="category"
          width={90}
          tick={{ fill: "#9ca3af", fontSize: 11 }}
          tickLine={false}
          axisLine={false}
        />
        <Tooltip
          content={<CustomTooltip currency={currency} />}
          cursor={{ fill: "rgba(255,255,255,0.03)" }}
        />
        <Bar dataKey="amount" radius={[0, 4, 4, 0]} maxBarSize={18}>
          {sorted.map((entry, i) => (
            <Cell
              key={entry.category}
              fill={entry.color ?? DEFAULT_COLORS[i % DEFAULT_COLORS.length]}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
};
