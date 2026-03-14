import React from "react";
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import type { SpendingAnalysis } from "@/domain/SpendingAnalysis";

const COLORS = [
  "#06B6D4",
  "#8B5CF6",
  "#10B981",
  "#F59E0B",
  "#EF4444",
  "#3B82F6",
  "#EC4899",
  "#14B8A6",
  "#F97316",
  "#6366F1",
  "#84CC16",
  "#A78BFA",
  "#FB923C",
];

const CATEGORY_LABELS: Record<string, string> = {
  LOYER: "Loyer",
  SALAIRE: "Salaires",
  ALIMENTATION: "Alimentation",
  TRANSPORT: "Transport",
  ABONNEMENT: "Abonnements",
  ENERGIE: "Énergie",
  TELECOM: "Télécom",
  ASSURANCE: "Assurance",
  CHARGES_FISCALES: "Charges fiscales",
  FOURNISSEUR: "Fournisseurs",
  CLIENT_PAYMENT: "Paiements clients",
  VIREMENT: "Virements",
  AUTRE: "Autre",
};

interface SpendingDonutProps {
  data: SpendingAnalysis;
  height?: number;
}

const CustomTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  const d = payload[0];
  return (
    <div className="glass-card p-3 text-xs shadow-card">
      <p className="text-white font-medium">{d.name}</p>
      <p className="text-primary">
        {new Intl.NumberFormat("fr-FR", {
          style: "currency",
          currency: "EUR",
        }).format(d.value)}
      </p>
    </div>
  );
};

export const SpendingDonut: React.FC<SpendingDonutProps> = ({
  data,
  height = 280,
}) => {
  const chartData = Object.entries(data.byCategory)
    .filter(([, v]) => v && v > 0)
    .map(([key, value]) => ({
      name: CATEGORY_LABELS[key] ?? key,
      value: value!,
    }))
    .sort((a, b) => b.value - a.value);

  if (!chartData.length) {
    return (
      <div className="flex items-center justify-center h-40 text-text-muted text-sm">
        Aucune dépense sur la période
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie
          data={chartData}
          cx="50%"
          cy="50%"
          innerRadius={70}
          outerRadius={110}
          paddingAngle={3}
          dataKey="value"
        >
          {chartData.map((_, i) => (
            <Cell key={i} fill={COLORS[i % COLORS.length]} />
          ))}
        </Pie>
        <Tooltip content={<CustomTooltip />} />
        <Legend
          iconType="circle"
          iconSize={8}
          formatter={(value) => (
            <span className="text-text-secondary text-xs">{value}</span>
          )}
        />
      </PieChart>
    </ResponsiveContainer>
  );
};
