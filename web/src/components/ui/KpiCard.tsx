import React from "react";
import { TrendingUp, TrendingDown, Minus } from "lucide-react";
import { Card } from "./Card";

interface KpiCardProps {
  label: string;
  value: string | number;
  delta?: number; // percentage change, positive = good, negative = bad
  deltaLabel?: string;
  icon?: React.ReactNode;
  isLoading?: boolean;
  invertDelta?: boolean; // for expenses: positive delta is bad
}

export const KpiCard: React.FC<KpiCardProps> = ({
  label,
  value,
  delta,
  deltaLabel,
  icon,
  isLoading = false,
  invertDelta = false,
}) => {
  if (isLoading) {
    return (
      <Card className="animate-pulse">
        <div className="h-4 bg-white/10 rounded w-24 mb-3" />
        <div className="h-8 bg-white/10 rounded w-32 mb-2" />
        <div className="h-3 bg-white/10 rounded w-20" />
      </Card>
    );
  }

  const isPositive = invertDelta ? (delta ?? 0) < 0 : (delta ?? 0) > 0;
  const isNeutral = delta === 0 || delta === undefined;

  let deltaColor = "text-text-secondary";
  if (!isNeutral) deltaColor = isPositive ? "text-success" : "text-danger";

  let DeltaIcon = Minus;
  if (!isNeutral) DeltaIcon = isPositive ? TrendingUp : TrendingDown;

  return (
    <Card>
      <div className="flex items-start justify-between">
        <p className="text-text-secondary text-sm font-medium">{label}</p>
        {icon && (
          <div className="p-2 rounded-lg bg-primary/10 text-primary">
            {icon}
          </div>
        )}
      </div>
      <p className="text-2xl font-bold text-white mt-2 mb-1">{value}</p>
      {delta !== undefined && (
        <div
          className={`flex items-center gap-1 text-xs font-medium ${deltaColor}`}
        >
          <DeltaIcon size={12} />
          <span>
            {Math.abs(delta).toFixed(1)}%{deltaLabel ? ` ${deltaLabel}` : ""}
          </span>
        </div>
      )}
    </Card>
  );
};
