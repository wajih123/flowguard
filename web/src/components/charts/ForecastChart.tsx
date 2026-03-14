import React, { useState } from "react";
import {
  ResponsiveContainer,
  ComposedChart,
  Area,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceArea,
} from "recharts";
import { format, parseISO, isAfter, startOfDay } from "date-fns";
import { fr } from "date-fns/locale";
import { Lock } from "lucide-react";
import type { TreasuryForecast } from "@/domain/TreasuryForecast";
import {
  ConfidenceBadge,
  type ConfidenceLevel,
} from "@/components/ui/ConfidenceBadge";

interface ForecastChartProps {
  data: TreasuryForecast;
  height?: number;
  userRole?: "ROLE_USER" | "ROLE_BUSINESS";
  horizon?: number;
  onHorizonChange?: (h: number) => void;
  onUpgrade?: () => void;
}

const HORIZONS = [
  { label: "30j", value: 30 },
  { label: "60j", value: 60 },
  { label: "90j", value: 90 },
];

const fmtEuro = (v: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(v);

const confidenceToLevel = (score: number): ConfidenceLevel => {
  if (score >= 0.8) return "HIGH";
  if (score >= 0.65) return "MEDIUM";
  if (score >= 0.45) return "LOW";
  return "INSUFFICIENT";
};

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  const b = payload.find(
    (p: any) => p.dataKey === "balance" || p.dataKey === "histBalance",
  );
  const lower = payload.find((p: any) => p.dataKey === "lower");
  const upper = payload.find((p: any) => p.dataKey === "upper");
  const value = b?.value;
  const isFuture = !!payload.find((p: any) => p.dataKey === "balance");
  const causeLabel = payload[0]?.payload?.causeLabel;

  return (
    <div className="bg-surface-elevated border border-white/10 rounded-xl p-3 shadow-modal text-xs min-w-[160px]">
      <p
        className="text-text-secondary mb-1.5"
        style={{ fontFamily: "var(--font-display)" }}
      >
        {label}
      </p>
      {value !== undefined && (
        <p
          className={`font-medium text-sm ${
            value < 0
              ? "text-danger"
              : value < 500
                ? "text-warning"
                : "text-white"
          }`}
          style={{ fontFamily: "var(--font-numeric)" }}
        >
          {fmtEuro(value)}
        </p>
      )}
      {lower && upper && (
        <p className="text-text-muted mt-1">
          Fourchette : {fmtEuro(lower.value)} â€” {fmtEuro(upper.value)}
        </p>
      )}
      {isFuture && causeLabel && (
        <p className="text-warning mt-1 font-medium">{causeLabel}</p>
      )}
      {isFuture && value !== undefined && (
        <p className="text-text-muted mt-1">
          {value < 200 ? "âš  Solde critique prÃ©vu" : "PrÃ©vision IA"}
        </p>
      )}
    </div>
  );
};

export const ForecastChart: React.FC<ForecastChartProps> = ({
  data,
  height = 300,
  userRole = "ROLE_USER",
  horizon = 30,
  onHorizonChange,
  onUpgrade,
}) => {
  const [showUpgrade, setShowUpgrade] = useState(false);
  const today = startOfDay(new Date());

  const criticalDates = new Set(
    data.criticalPoints.map((c) =>
      format(parseISO(c.date), "d MMM", { locale: fr }),
    ),
  );

  const chartData = data.predictions.map((p) => {
    const dateLabel = format(parseISO(p.date), "d MMM", { locale: fr });
    const isFuture = isAfter(parseISO(p.date), today);
    const causeLabel = data.criticalPoints.find(
      (c) => format(parseISO(c.date), "d MMM", { locale: fr }) === dateLabel,
    )?.reason;

    return {
      date: dateLabel,
      balance: isFuture ? Math.round(p.predictedBalance) : undefined,
      histBalance: !isFuture ? Math.round(p.predictedBalance) : undefined,
      lower: isFuture ? Math.round(p.lowerBound) : undefined,
      upper: isFuture ? Math.round(p.upperBound) : undefined,
      causeLabel,
      isFuture,
    };
  });

  const todayLabel = format(today, "d MMM", { locale: fr });

  const handleHorizonClick = (h: number) => {
    if (h > 30 && userRole === "ROLE_USER") {
      setShowUpgrade(true);
      onUpgrade?.();
      return;
    }
    setShowUpgrade(false);
    onHorizonChange?.(h);
  };

  const confidenceLevel = confidenceToLevel(data.confidenceScore);

  return (
    <div className="space-y-3">
      {/* Header: title + horizon selector */}
      <div className="flex items-center justify-between px-1">
        <div className="flex items-center gap-2">
          <p
            className="text-white font-semibold text-sm"
            style={{ fontFamily: "var(--font-display)" }}
          >
            PrÃ©vision de trÃ©sorerie
          </p>
          <ConfidenceBadge level={confidenceLevel} />
        </div>

        <div className="flex items-center gap-1 bg-surface rounded-xl p-1 border border-white/[0.06]">
          {HORIZONS.map((h) => {
            const locked = h.value > 30 && userRole === "ROLE_USER";
            const active = h.value === horizon;
            return (
              <button
                key={h.value}
                onClick={() => handleHorizonClick(h.value)}
                className={`
                  relative flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium
                  transition-all duration-150
                  ${
                    active
                      ? "bg-primary/15 text-primary"
                      : locked
                        ? "text-text-muted cursor-pointer hover:text-text-secondary"
                        : "text-text-secondary hover:text-white"
                  }
                `}
              >
                {h.label}
                {locked && <Lock size={9} className="opacity-50" />}
              </button>
            );
          })}
        </div>
      </div>

      {/* Subtle PRO upsell â€” only if clicked, not aggressive */}
      {showUpgrade && (
        <div className="text-xs text-text-secondary px-1 flex items-center gap-1.5 animate-fade-in">
          <Lock size={11} />
          <span>
            Horizon 60j / 90j disponible avec{" "}
            <button
              onClick={onUpgrade}
              className="text-primary underline underline-offset-2"
            >
              FlowGuard Pro
            </button>
          </span>
        </div>
      )}

      {/* Chart */}
      <ResponsiveContainer width="100%" height={height}>
        <ComposedChart
          data={chartData}
          margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
        >
          <defs>
            <linearGradient id="balanceFutureGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#06B6D4" stopOpacity={0.18} />
              <stop offset="95%" stopColor="#06B6D4" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="balanceHistGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#A0AEC0" stopOpacity={0.1} />
              <stop offset="95%" stopColor="#A0AEC0" stopOpacity={0} />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            stroke="rgba(255,255,255,0.04)"
            vertical={false}
          />

          <XAxis
            dataKey="date"
            tick={{
              fill: "rgba(255,255,255,0.35)",
              fontSize: 11,
              fontFamily: "Inter",
            }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tickFormatter={(v) => `${(v / 1000).toFixed(0)}kâ‚¬`}
            tick={{
              fill: "rgba(255,255,255,0.35)",
              fontSize: 11,
              fontFamily: "Inter",
            }}
            axisLine={false}
            tickLine={false}
            width={42}
          />

          <Tooltip
            content={<CustomTooltip />}
            cursor={{ stroke: "rgba(255,255,255,0.08)", strokeWidth: 1 }}
          />

          {/* Danger threshold line */}
          <ReferenceLine
            y={200}
            stroke="#EF4444"
            strokeDasharray="4 3"
            strokeOpacity={0.45}
            strokeWidth={1}
          />

          {/* Today vertical line */}
          <ReferenceLine
            x={todayLabel}
            stroke="rgba(255,255,255,0.25)"
            strokeWidth={1}
            label={{
              value: "Auj.",
              position: "insideTopRight",
              fill: "rgba(255,255,255,0.35)",
              fontSize: 10,
              fontFamily: "Inter",
            }}
          />

          {/* Uncertainty band (p25â€“p75) */}
          <Area
            type="monotone"
            dataKey="upper"
            fill="rgba(6,182,212,0.07)"
            stroke="none"
            connectNulls
          />
          <Area
            type="monotone"
            dataKey="lower"
            fill="rgba(6,182,212,0.07)"
            stroke="rgba(6,182,212,0.15)"
            strokeWidth={1}
            strokeDasharray="3 3"
            fillOpacity={0}
            connectNulls
          />

          {/* Historical balance (muted) */}
          <Area
            type="monotone"
            dataKey="histBalance"
            stroke="#A0AEC0"
            strokeWidth={2}
            strokeOpacity={0.5}
            fill="url(#balanceHistGrad)"
            dot={false}
            connectNulls
          />

          {/* Future predicted balance (primary) */}
          <Area
            type="monotone"
            dataKey="balance"
            stroke="#06B6D4"
            strokeWidth={2.5}
            fill="url(#balanceFutureGrad)"
            strokeDasharray={undefined}
            dot={(props: any) => {
              const isCritical = criticalDates.has(props.payload?.date);
              if (!isCritical) return <React.Fragment key={props.key} />;
              return (
                <polygon
                  key={props.key}
                  points={`${props.cx},${props.cy - 7} ${props.cx + 6},${props.cy + 4} ${props.cx - 6},${props.cy + 4}`}
                  fill="#F59E0B"
                  stroke="#F59E0B"
                  strokeWidth={1}
                />
              );
            }}
            activeDot={{
              r: 5,
              fill: "#06B6D4",
              stroke: "rgba(6,182,212,0.4)",
              strokeWidth: 4,
            }}
            connectNulls
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
};
