import React, { useState } from "react";
import { Activity, CheckCircle, AlertTriangle } from "lucide-react";
import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
  ReferenceLine,
} from "recharts";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { forecastAccuracyApi } from "@/api/forecastAccuracy";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const fmtPct = (n: number) => `${n.toFixed(1)}%`;

const HORIZONS = [
  { value: 7, label: "7 jours" },
  { value: 30, label: "30 jours" },
  { value: 90, label: "90 jours" },
];

const CustomTooltip: React.FC<any> = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-bg-card border border-white/10 rounded-xl px-4 py-3 text-sm shadow-xl">
      <p className="font-semibold text-white mb-2">{label}</p>
      {payload.map((p: any) => (
        <p key={p.name} style={{ color: p.stroke || p.fill }}>
          {p.name}: {p.name.includes("%") ? fmtPct(p.value) : fmt(p.value)}
        </p>
      ))}
    </div>
  );
};

const ForecastAccuracyPage: React.FC = () => {
  const [horizonDays, setHorizonDays] = useState(30);

  const { data: summary, isLoading: loadingSummary } = useQuery({
    queryKey: ["forecast-accuracy-summary"],
    queryFn: forecastAccuracyApi.getSummary,
  });

  const { data: entries, isLoading: loadingEntries } = useQuery({
    queryKey: ["forecast-accuracy", horizonDays],
    queryFn: () => forecastAccuracyApi.getByHorizon(horizonDays),
  });

  const chartData = (entries ?? [])
    .filter((e) => e.actualBalance !== null)
    .map((e) => ({
      date: format(parseISO(e.forecastDate), "d MMM", { locale: fr }),
      Prévu: e.predictedBalance,
      Réel: e.actualBalance,
      "Précision (%)": e.accuracyPct,
    }))
    .slice(-30); // last 30 points

  const avgAcc = summary?.averageAccuracyPct;

  return (
    <Layout title="Précision des prévisions">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div>
            <h1 className="page-title">Précision des prévisions</h1>
            <p className="page-subtitle">
              Mesurez l'écart entre les prévisions FlowGuard et la réalité
            </p>
          </div>
          <div className="flex gap-2">
            {HORIZONS.map((h) => (
              <button
                key={h.value}
                onClick={() => setHorizonDays(h.value)}
                className={`px-3 py-1.5 text-sm rounded-lg border transition ${
                  horizonDays === h.value
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-white/10 text-text-muted hover:border-white/20"
                }`}
              >
                {h.label}
              </button>
            ))}
          </div>
        </div>

        {/* KPIs */}
        {loadingSummary ? (
          <div className="grid grid-cols-3 gap-4">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="h-20 rounded-xl bg-white/[0.04] animate-pulse"
              />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-4">
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Précision moyenne
              </p>
              <p
                className={`text-2xl font-bold font-numeric ${
                  avgAcc == null
                    ? "text-text-muted"
                    : avgAcc >= 90
                      ? "text-success"
                      : avgAcc >= 75
                        ? "text-warning"
                        : "text-danger"
                }`}
              >
                {avgAcc != null ? fmtPct(avgAcc) : "—"}
              </p>
            </Card>
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Prévisions réconciliées
              </p>
              <p className="text-2xl font-bold font-numeric text-white">
                {summary?.reconciled ?? 0}
                <span className="text-sm text-text-muted ml-1">
                  / {summary?.totalEntries ?? 0}
                </span>
              </p>
            </Card>
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
                Statut modèle
              </p>
              <div className="flex items-center gap-2">
                {avgAcc == null ? (
                  <AlertTriangle size={18} className="text-text-muted" />
                ) : avgAcc >= 85 ? (
                  <CheckCircle size={18} className="text-success" />
                ) : (
                  <AlertTriangle size={18} className="text-warning" />
                )}
                <p className="text-white text-sm font-medium">
                  {avgAcc == null
                    ? "Pas de données"
                    : avgAcc >= 85
                      ? "Modèle précis"
                      : avgAcc >= 70
                        ? "Précision correcte"
                        : "Amélioration requise"}
                </p>
              </div>
            </Card>
          </div>
        )}

        {loadingEntries ? (
          <Loader text="Chargement…" />
        ) : !chartData.length ? (
          <Card>
            <div className="py-12 text-center text-text-muted">
              <Activity className="mx-auto mb-3 opacity-30" size={36} />
              <p>Aucune donnée réconciliée pour cet horizon.</p>
              <p className="text-xs mt-1">
                Les données apparaissent après la réconciliation automatique.
              </p>
            </div>
          </Card>
        ) : (
          <>
            {/* Predicted vs Actual chart */}
            <Card>
              <CardHeader title={`Prévu vs Réel — Horizon ${horizonDays}j`} />
              <div className="mt-4 h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={chartData}>
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="rgba(255,255,255,0.05)"
                    />
                    <XAxis
                      dataKey="date"
                      tick={{ fill: "#8B9CA8", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      yAxisId="euro"
                      tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
                      tick={{ fill: "#8B9CA8", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend wrapperStyle={{ fontSize: 12, color: "#8B9CA8" }} />
                    <Line
                      yAxisId="euro"
                      type="monotone"
                      dataKey="Prévu"
                      stroke="rgba(101,163,213,0.6)"
                      strokeWidth={2}
                      dot={false}
                      strokeDasharray="5 3"
                    />
                    <Line
                      yAxisId="euro"
                      type="monotone"
                      dataKey="Réel"
                      stroke="rgba(101,213,163,0.9)"
                      strokeWidth={2}
                      dot={{ r: 2 }}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </Card>

            {/* Accuracy % over time */}
            <Card>
              <CardHeader title="Évolution de la précision (%)" />
              <div className="mt-4 h-52">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={chartData}>
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="rgba(255,255,255,0.05)"
                    />
                    <XAxis
                      dataKey="date"
                      tick={{ fill: "#8B9CA8", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      domain={[0, 100]}
                      tickFormatter={(v) => `${v}%`}
                      tick={{ fill: "#8B9CA8", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <ReferenceLine
                      y={85}
                      stroke="rgba(101,213,163,0.4)"
                      strokeDasharray="4 2"
                      label={{ value: "85%", fill: "#8B9CA8", fontSize: 10 }}
                    />
                    <Line
                      type="monotone"
                      dataKey="Précision (%)"
                      stroke="rgba(250,204,21,0.9)"
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </>
        )}
      </div>
    </Layout>
  );
};

export default ForecastAccuracyPage;
