import React from "react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

interface BalancePoint {
  date: string;
  balance: number;
}

interface BalanceLineChartProps {
  data: BalancePoint[];
  height?: number;
  color?: string;
}

const formatCurrency = (v: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(v);

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="glass-card p-3 text-xs shadow-card">
      <p className="text-text-secondary mb-1">{label}</p>
      <p className="text-primary font-semibold">
        {formatCurrency(payload[0]?.value)}
      </p>
    </div>
  );
};

export const BalanceLineChart: React.FC<BalanceLineChartProps> = ({
  data,
  height = 200,
  color = "#06B6D4",
}) => {
  const chartData = data.map((d) => ({
    date: format(parseISO(d.date), "d MMM", { locale: fr }),
    balance: Math.round(d.balance),
  }));

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart
        data={chartData}
        margin={{ top: 5, right: 10, left: 0, bottom: 0 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis
          dataKey="date"
          tick={{ fill: "rgba(255,255,255,0.4)", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
          tick={{ fill: "rgba(255,255,255,0.4)", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={45}
        />
        <Tooltip content={<CustomTooltip />} />
        <Line
          type="monotone"
          dataKey="balance"
          stroke={color}
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
};
