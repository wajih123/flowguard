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
} from "recharts";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";
import { TrendingDown, RefreshCw } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import {
  ConfidenceBadge,
  type ConfidenceLevel,
} from "@/components/ui/ConfidenceBadge";
import { AmountDisplay } from "@/components/ui/AmountDisplay";
import { formatAmountCompact } from "@/utils/format";
import type { Prediction } from "@/types";

interface ForecastChartProps {
  prediction?: Prediction;
  isLoading: boolean;
  onRegenerate?: () => void;
}

const HORIZONS = [
  { label: "30j", value: 30 },
  { label: "60j", value: 60 },
  { label: "90j", value: 90 },
];

const CustomTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  const point = payload[0]?.payload;
  const balance = point?.balance;
  const p25 = point?.p25;
  const p75 = point?.p75;

  return (
    <div className="bg-surface-elevated border border-white/10 rounded-xl p-3 shadow-modal text-xs min-w-[160px]">
      <p
        className="text-text-secondary mb-1.5"
        style={{ fontFamily: "var(--font-display)" }}
      >
        {point?.dateLabel}
      </p>
      {balance !== undefined && (
        <p
          className={`font-medium text-sm font-numeric ${
            balance < 0
              ? "text-danger"
              : balance < 500
                ? "text-warning"
                : "text-white"
          }`}
        >
          {formatAmountCompact(balance)}
        </p>
      )}
      {p25 !== undefined && p75 !== undefined && (
        <p className="text-text-muted mt-1">
          Fourchette : {formatAmountCompact(p25)} – {formatAmountCompact(p75)}
        </p>
      )}
      {point?.criticalLabel && (
        <p className="text-warning mt-1 font-medium">{point.criticalLabel}</p>
      )}
    </div>
  );
};

export const ForecastChart: React.FC<ForecastChartProps> = ({
  prediction,
  isLoading,
  onRegenerate,
}) => {
  const [horizon, setHorizon] = useState(30);

  if (isLoading) {
    return (
      <Card padding="md">
        <div className="h-72 rounded-xl bg-white/[0.06] animate-pulse" />
      </Card>
    );
  }

  if (!prediction || prediction.status === "ERROR") {
    return (
      <Card padding="md">
        <div className="flex flex-col items-center justify-center py-12 gap-3">
          <TrendingDown className="text-text-muted" size={32} />
          <p className="text-text-secondary text-sm">
            Prévision non disponible
          </p>
          {onRegenerate && (
            <Button
              variant="outline"
              size="sm"
              leftIcon={<RefreshCw size={14} />}
              onClick={onRegenerate}
            >
              Regénérer
            </Button>
          )}
        </div>
      </Card>
    );
  }

  const criticalDateMap = Object.fromEntries(
    prediction.criticalPoints.map((cp) => [cp.date, cp.label]),
  );

  const slicedData = prediction.dailyData.slice(0, horizon);
  const todayStr = format(new Date(), "yyyy-MM-dd");

  const chartData = slicedData.map((d) => ({
    ...d,
    dateLabel: format(parseISO(d.date), "d MMM", { locale: fr }),
    isToday: d.date === todayStr,
    criticalLabel: criticalDateMap[d.date],
  }));

  const confidenceLevel: ConfidenceLevel =
    prediction.confidenceScore >= 0.8
      ? "HIGH"
      : prediction.confidenceScore >= 0.65
        ? "MEDIUM"
        : prediction.confidenceScore >= 0.45
          ? "LOW"
          : "INSUFFICIENT";

  const balances = slicedData.map((d) => d.balance);
  const bounds = slicedData.flatMap((d) => [d.p25, d.p75]);
  const yMin = Math.min(...bounds) - 150;
  const yMax = Math.max(...bounds) + 150;

  const todayLabel = format(new Date(), "d MMM", { locale: fr });

  return (
    <Card padding="md">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div>
          <p className="text-text-secondary text-xs uppercase tracking-widest font-medium mb-1">
            Prévision de trésorerie
          </p>
          <ConfidenceBadge level={confidenceLevel} />
        </div>
        <div className="flex gap-1">
          {HORIZONS.map((h) => (
            <button
              key={h.value}
              onClick={() => setHorizon(h.value)}
              className={`min-h-[36px] px-3 py-1 rounded-lg text-xs font-medium transition-all ${
                horizon === h.value
                  ? "bg-primary/20 text-primary border border-primary/40"
                  : "text-text-muted hover:text-white hover:bg-white/[0.06]"
              }`}
            >
              {h.label}
            </button>
          ))}
        </div>
      </div>

      {/* Min balance highlight */}
      {prediction.deficitPredicted && prediction.deficitDate && (
        <div className="mb-4 flex items-center gap-3 bg-danger/[0.08] border border-danger/20 rounded-xl px-4 py-2.5">
          <span className="text-xs text-text-secondary">
            Solde minimum prévu :
          </span>
          <AmountDisplay amount={prediction.minPredictedBalance} size="sm" />
          <span className="text-xs text-text-muted">
            le{" "}
            {format(parseISO(prediction.minPredictedDate), "d MMMM", {
              locale: fr,
            })}
          </span>
        </div>
      )}

      {/* Chart */}
      <ResponsiveContainer width="100%" height={260}>
        <ComposedChart
          data={chartData}
          margin={{ top: 5, right: 10, left: 5, bottom: 0 }}
        >
          <defs>
            <linearGradient id="fgBalanceGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#06B6D4" stopOpacity={0.3} />
              <stop offset="95%" stopColor="#06B6D4" stopOpacity={0} />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            stroke="rgba(255,255,255,0.05)"
            vertical={false}
          />

          <XAxis
            dataKey="dateLabel"
            tick={{ fill: "#A0AEC0", fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            interval={Math.floor(horizon / 6)}
          />

          <YAxis
            tickFormatter={(v) => formatAmountCompact(v)}
            tick={{ fill: "#A0AEC0", fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            domain={[yMin, yMax]}
            width={80}
          />

          <Tooltip
            content={<CustomTooltip />}
            cursor={{ stroke: "rgba(255,255,255,0.1)" }}
          />

          {/* Zero line */}
          <ReferenceLine
            y={0}
            stroke="rgba(239,68,68,0.35)"
            strokeDasharray="4 4"
            label={{
              value: "0 €",
              fill: "#EF4444",
              fontSize: 10,
              position: "insideTopRight",
            }}
          />

          {/* Today reference */}
          <ReferenceLine
            x={todayLabel}
            stroke="rgba(255,255,255,0.2)"
            strokeDasharray="3 3"
            label={{
              value: "Auj.",
              fill: "#A0AEC0",
              fontSize: 10,
              position: "insideTopRight",
            }}
          />

          {/* Uncertainty band — upper */}
          <Area
            type="monotone"
            dataKey="p75"
            stroke="none"
            fill="#06B6D4"
            fillOpacity={0.07}
          />
          {/* Uncertainty band — lower (covers the fill back to bg) */}
          <Area
            type="monotone"
            dataKey="p25"
            stroke="none"
            fill="#111C44"
            fillOpacity={1}
          />

          {/* Main balance line with gradient fill */}
          <Area
            type="monotone"
            dataKey="balance"
            stroke="#06B6D4"
            strokeWidth={2}
            fill="url(#fgBalanceGrad)"
            dot={false}
            activeDot={{ r: 5, fill: "#06B6D4", strokeWidth: 0 }}
          />
        </ComposedChart>
      </ResponsiveContainer>

      {/* Critical points legend */}
      {prediction.criticalPoints.filter((cp) =>
        slicedData.some((d) => d.date === cp.date),
      ).length > 0 && (
        <div className="mt-4 border-t border-white/[0.06] pt-3 flex gap-4 flex-wrap">
          {prediction.criticalPoints
            .filter((cp) => slicedData.some((d) => d.date === cp.date))
            .map((cp) => (
              <div key={cp.date} className="flex items-center gap-1.5 text-xs">
                <span
                  className={`w-2 h-2 rounded-full flex-shrink-0 ${
                    cp.amount < 0 ? "bg-danger" : "bg-success"
                  }`}
                />
                <span className="text-text-secondary">
                  {cp.label} ·{" "}
                  {format(parseISO(cp.date), "d MMM", { locale: fr })} ·
                </span>
                <span
                  className={`font-numeric font-medium ${cp.amount < 0 ? "text-danger" : "text-success"}`}
                >
                  {cp.amount > 0 ? "+" : ""}
                  {formatAmountCompact(cp.amount)}
                </span>
              </div>
            ))}
        </div>
      )}
    </Card>
  );
};
