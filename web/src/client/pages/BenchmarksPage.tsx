import React, { useState } from "react";
import { BarChart2, TrendingUp, TrendingDown, Minus } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { benchmarksApi } from "@/api/benchmarks";
import type { UserBenchmark } from "@/api/benchmarks";

const SECTOR_LABELS: Record<string, string> = {
  IT_FREELANCE: "Freelance IT / Dev",
  CONSULTING: "Conseil & Management",
  ECOMMERCE: "E-commerce",
  FOOD_BEVERAGE: "Restauration / Food",
};

const SIZE_LABELS: Record<string, string> = {
  SOLO: "Solo / Micro",
  SMALL: "PME (2–9 salariés)",
  MEDIUM: "ETI (10–49 salariés)",
};

const PERCENTILE_CONFIG = {
  BOTTOM_25: {
    label: "Quartile bas",
    color: "bg-danger",
    text: "text-danger",
    width: "25%",
  },
  Q1_Q2: {
    label: "Médian bas",
    color: "bg-warning",
    text: "text-warning",
    width: "50%",
  },
  Q2_Q3: {
    label: "Médian haut",
    color: "bg-success",
    text: "text-success",
    width: "75%",
  },
  TOP_25: {
    label: "Top quartile",
    color: "bg-primary",
    text: "text-primary",
    width: "100%",
  },
} as const;

const PercentileBar: React.FC<{ benchmark: UserBenchmark }> = ({
  benchmark,
}) => {
  const cfg =
    PERCENTILE_CONFIG[
      benchmark.percentileBand as keyof typeof PERCENTILE_CONFIG
    ] ?? PERCENTILE_CONFIG.Q1_Q2;
  return (
    <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium text-white text-sm">
            {benchmark.metricName}
          </p>
          <p className="text-text-muted text-xs mt-0.5">{benchmark.insight}</p>
        </div>
        <div className="text-right shrink-0">
          <span
            className={`text-xs font-medium px-2 py-0.5 rounded-full bg-white/[0.06] ${cfg.text}`}
          >
            {cfg.label}
          </span>
        </div>
      </div>

      {/* percentile bar with P25 / P50 / P75 markers */}
      <div className="relative">
        <div className="flex justify-between text-xs text-text-muted mb-1">
          <span>P25: {fmtValue(benchmark.p25, benchmark.unit)}</span>
          <span>P50: {fmtValue(benchmark.p50, benchmark.unit)}</span>
          <span>P75: {fmtValue(benchmark.p75, benchmark.unit)}</span>
        </div>
        <div className="w-full h-2 bg-white/[0.06] rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${cfg.color}`}
            style={{ width: cfg.width }}
          />
        </div>
        <div className="flex justify-between text-xs text-text-muted mt-1">
          <span>Bas</span>
          <span className={`font-semibold ${cfg.text}`}>Votre position</span>
          <span>Haut</span>
        </div>
      </div>

      <p className="text-white text-sm">
        <span className="text-text-muted mr-2">Votre valeur :</span>
        <span className="font-numeric font-semibold">
          {fmtValue(benchmark.userValue, benchmark.unit)}
        </span>
      </p>
    </div>
  );
};

function fmtValue(v: number, unit: string): string {
  if (unit === "EUR" || unit === "€") {
    return new Intl.NumberFormat("fr-FR", {
      style: "currency",
      currency: "EUR",
    }).format(v);
  }
  if (unit === "%") return `${v.toFixed(1)}%`;
  return `${v} ${unit}`;
}

const BenchmarksPage: React.FC = () => {
  const [sector, setSector] = useState("IT_FREELANCE");
  const [companySize, setCompanySize] = useState("SOLO");

  const { data: sectors, isLoading: loadingSectors } = useQuery({
    queryKey: ["benchmark-sectors"],
    queryFn: benchmarksApi.getSectors,
  });

  const { data: comparison, isLoading: loadingComp } = useQuery({
    queryKey: ["benchmark-compare", sector, companySize],
    queryFn: () => benchmarksApi.compare(sector, companySize),
    enabled: !!sector,
  });

  const topCount =
    comparison?.filter(
      (b) => b.percentileBand === "TOP_25" || b.percentileBand === "Q2_Q3",
    ).length ?? 0;
  const total = comparison?.length ?? 0;

  return (
    <Layout title="Benchmarks">
      <div className="max-w-4xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div>
            <h1 className="page-title">Benchmark sectoriel</h1>
            <p className="page-subtitle">
              Comparez vos performances financières à votre secteur
            </p>
          </div>
          <div className="flex gap-3">
            <select
              className="fg-input"
              value={sector}
              onChange={(e) => setSector(e.target.value)}
            >
              {loadingSectors ? (
                <option>Chargement…</option>
              ) : (
                (sectors ?? Object.keys(SECTOR_LABELS)).map((s) => (
                  <option key={s} value={s}>
                    {SECTOR_LABELS[s] ?? s}
                  </option>
                ))
              )}
            </select>
            <select
              className="fg-input"
              value={companySize}
              onChange={(e) => setCompanySize(e.target.value)}
            >
              {Object.entries(SIZE_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Summary KPIs */}
        {comparison && (
          <div className="grid grid-cols-3 gap-4">
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
                Secteur{" "}
                <HelpTooltip text="Secteur d'activité sélectionné comme référence pour la comparaison. Changez-le dans le menu du haut." />
              </p>
              <p className="text-white font-semibold">
                {SECTOR_LABELS[sector] ?? sector}
              </p>
            </Card>
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
                Métriques au-dessus médiane{" "}
                <HelpTooltip text="Nombre d'indicateurs où vos performances surpassent la médiane des entreprises du même secteur." />
              </p>
              <p className="text-2xl font-bold font-numeric text-success">
                {topCount} / {total}
              </p>
            </Card>
            <Card padding="sm">
              <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
                Taille d'entreprise{" "}
                <HelpTooltip text="Segment de taille utilisé pour sélectionner les entreprises références dans la comparaison." />
              </p>
              <p className="text-white font-semibold">
                {SIZE_LABELS[companySize] ?? companySize}
              </p>
            </Card>
          </div>
        )}

        {loadingComp ? (
          <Loader text="Chargement des benchmarks…" />
        ) : !comparison?.length ? (
          <Card>
            <div className="py-12 text-center text-text-muted">
              <BarChart2 className="mx-auto mb-3 opacity-30" size={36} />
              <p>Aucun benchmark disponible pour ce secteur.</p>
            </div>
          </Card>
        ) : (
          <Card>
            <CardHeader
              title={`${comparison.length} indicateurs — ${SECTOR_LABELS[sector] ?? sector} · ${SIZE_LABELS[companySize]}`}
              helpTooltip={
                <HelpTooltip text="Positionnez vos indicateurs financiers clés par rapport aux entreprises de votre secteur et de votre taille." />
              }
            />
            <div className="mt-5 space-y-3">
              {comparison.map((b, i) => (
                <PercentileBar key={i} benchmark={b} />
              ))}
            </div>
          </Card>
        )}
      </div>
    </Layout>
  );
};

export default BenchmarksPage;
