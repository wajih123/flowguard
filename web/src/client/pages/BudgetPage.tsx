import React, { useState } from "react";
import {
  Target,
  TrendingUp,
  TrendingDown,
  Minus,
  RefreshCw,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from "recharts";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { budgetApi } from "@/api/budget";
import type { BudgetVsActualLine } from "@/api/budget";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const MONTHS = [
  "Janvier",
  "Février",
  "Mars",
  "Avril",
  "Mai",
  "Juin",
  "Juillet",
  "Août",
  "Septembre",
  "Octobre",
  "Novembre",
  "Décembre",
];

const STATUS_CONFIG: Record<
  string,
  { label: string; colorClass: string; icon: React.ElementType }
> = {
  OVER_BUDGET: {
    label: "Dépassé",
    colorClass: "text-danger",
    icon: TrendingUp,
  },
  ON_TRACK: { label: "Dans budget", colorClass: "text-success", icon: Minus },
  UNDER_BUDGET: {
    label: "Sous budget",
    colorClass: "text-primary",
    icon: TrendingDown,
  },
  UNBUDGETED: {
    label: "Non budgété",
    colorClass: "text-text-muted",
    icon: Minus,
  },
};

const CustomTooltip: React.FC<any> = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-bg-card border border-white/10 rounded-xl px-4 py-3 text-sm shadow-xl">
      <p className="font-semibold text-white mb-2">{label}</p>
      {payload.map((p: any) => (
        <p key={p.name} style={{ color: p.fill }}>
          {p.name}: {fmt(p.value)}
        </p>
      ))}
    </div>
  );
};

const BudgetPage: React.FC = () => {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [editCategory, setEditCategory] = useState<string | null>(null);
  const [editAmount, setEditAmount] = useState("");
  const qc = useQueryClient();

  const { data: vsActual, isLoading } = useQuery({
    queryKey: ["budget-vs-actual", year, month],
    queryFn: () => budgetApi.vsActual(year, month),
  });

  const { data: categories } = useQuery({
    queryKey: ["budget-categories", year, month],
    queryFn: () => budgetApi.get(year, month),
  });

  const upsertMut = useMutation({
    mutationFn: ({ category, amount }: { category: string; amount: number }) =>
      budgetApi.upsert(year, month, category, amount),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["budget-vs-actual", year, month] });
      setEditCategory(null);
    },
  });

  const deleteMut = useMutation({
    mutationFn: budgetApi.deleteLine,
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["budget-vs-actual", year, month] }),
  });

  const chartData =
    vsActual?.lines.map((l) => ({
      name: l.category.replace(/_/g, " "),
      Budget: l.budgeted,
      Réel: l.actual,
    })) ?? [];

  const totalBudgeted =
    vsActual?.lines.reduce((s, l) => s + l.budgeted, 0) ?? 0;
  const totalActual = vsActual?.lines.reduce((s, l) => s + l.actual, 0) ?? 0;
  const totalVariance = totalActual - totalBudgeted;

  return (
    <Layout title="Budget">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1 className="page-title">Budget mensuel</h1>
            <p className="page-subtitle">
              Comparez vos dépenses réelles à votre budget prévisionnel
            </p>
          </div>
          <div className="flex gap-3">
            <select
              className="fg-input"
              value={month}
              onChange={(e) => setMonth(Number(e.target.value))}
            >
              {MONTHS.map((m, i) => (
                <option key={i + 1} value={i + 1}>
                  {m}
                </option>
              ))}
            </select>
            <select
              className="fg-input"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
            >
              {[
                now.getFullYear() - 1,
                now.getFullYear(),
                now.getFullYear() + 1,
              ].map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* KPIs */}
        <div className="grid grid-cols-3 gap-4">
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Budget total{" "}
              <HelpTooltip text="Somme des montants budgétés sur toutes les catégories du mois sélectionné." />
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {fmt(totalBudgeted)}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Dépenses réelles{" "}
              <HelpTooltip text="Total des dépenses effectivement constatées sur vos comptes bancaires pour la période." />
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {fmt(totalActual)}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Écart{" "}
              <HelpTooltip text="Différence entre dépenses réelles et budget. Positif (rouge) = dépassement, négatif (bleu) = économie." />
            </p>
            <p
              className={`text-2xl font-bold font-numeric ${totalVariance > 0 ? "text-danger" : totalVariance < 0 ? "text-primary" : "text-success"}`}
            >
              {totalVariance > 0 ? "+" : ""}
              {fmt(totalVariance)}
            </p>
          </Card>
        </div>

        {isLoading ? (
          <Loader text="Chargement…" />
        ) : (
          <>
            {/* Chart */}
            {chartData.length > 0 && (
              <Card>
                <CardHeader
                  title="Budget vs Réel par catégorie"
                  action={
                    <HelpTooltip text="Comparaison graphique des montants budgétés et des dépenses réelles par catégorie." />
                  }
                />
                <div className="mt-4 h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} barGap={4}>
                      <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="rgba(255,255,255,0.05)"
                      />
                      <XAxis
                        dataKey="name"
                        tick={{ fill: "#8B9CA8", fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <YAxis
                        tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
                        tick={{ fill: "#8B9CA8", fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <Tooltip content={<CustomTooltip />} />
                      <Legend
                        wrapperStyle={{ fontSize: 12, color: "#8B9CA8" }}
                      />
                      <Bar
                        dataKey="Budget"
                        fill="rgba(101,163,213,0.4)"
                        radius={[4, 4, 0, 0]}
                      />
                      <Bar
                        dataKey="Réel"
                        fill="rgba(101,163,213,0.9)"
                        radius={[4, 4, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            )}

            {/* Line-by-line table */}
            <Card>
              <CardHeader
                title="Détail par catégorie"
                action={
                  <div className="flex items-center gap-2">
                    <HelpTooltip text="Suivi ligne par ligne des dépenses par catégorie avec statuts : dépassé, dans le budget ou sous le budget." />
                    <Button
                      variant="ghost"
                      size="sm"
                      className="gap-1"
                      onClick={() => setEditCategory("NEW")}
                    >
                      <Target size={14} /> Ajouter
                    </Button>
                  </div>
                }
              />
              {!vsActual?.lines.length ? (
                <div className="py-10 text-center text-text-muted">
                  <p>Aucun budget défini pour cette période.</p>
                </div>
              ) : (
                <div className="mt-4 space-y-2">
                  {vsActual.lines.map((line: BudgetVsActualLine) => {
                    const cfg = STATUS_CONFIG[line.status];
                    const isEditing = editCategory === line.category;
                    return (
                      <div
                        key={line.category}
                        className="flex items-center justify-between gap-4 px-4 py-3 bg-white/[0.02] border border-white/[0.06] rounded-xl"
                      >
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-0.5">
                            <p className="font-medium text-white text-sm capitalize">
                              {line.category.replace(/_/g, " ").toLowerCase()}
                            </p>
                            <span
                              className={`text-xs ${cfg.colorClass} flex items-center gap-0.5`}
                            >
                              <cfg.icon size={11} />
                              {cfg.label}
                            </span>
                          </div>
                          {/* Progress bar */}
                          <div className="w-full h-1.5 bg-white/[0.06] rounded-full mt-1">
                            <div
                              className={`h-full rounded-full transition-all ${line.status === "OVER_BUDGET" ? "bg-danger" : line.status === "ON_TRACK" ? "bg-success" : "bg-primary"}`}
                              style={{
                                width: `${Math.min(100, line.budgeted > 0 ? (line.actual / line.budgeted) * 100 : 0)}%`,
                              }}
                            />
                          </div>
                        </div>
                        <div className="text-right shrink-0 w-48">
                          {isEditing ? (
                            <div className="flex gap-2">
                              <input
                                className="fg-input py-1 text-sm w-24"
                                type="number"
                                min="0"
                                value={editAmount}
                                onChange={(e) => setEditAmount(e.target.value)}
                                autoFocus
                              />
                              <Button
                                size="sm"
                                onClick={() =>
                                  upsertMut.mutate({
                                    category: line.category,
                                    amount: Number(editAmount),
                                  })
                                }
                              >
                                OK
                              </Button>
                            </div>
                          ) : (
                            <div>
                              <p className="text-white text-sm font-numeric">
                                {fmt(line.actual)}{" "}
                                <span className="text-text-muted">
                                  / {fmt(line.budgeted)}
                                </span>
                              </p>
                              <p
                                className={`text-xs font-numeric ${line.variance > 0 ? "text-danger" : "text-primary"}`}
                              >
                                {line.variance > 0 ? "+" : ""}
                                {fmt(line.variance)}
                              </p>
                            </div>
                          )}
                        </div>
                        <div className="flex gap-1 shrink-0">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setEditCategory(line.category);
                              setEditAmount(String(line.budgeted));
                            }}
                          >
                            Modifier
                          </Button>
                          {(() => {
                            const catId = categories?.find(
                              (c) => c.category === line.category,
                            )?.id;
                            return catId ? (
                              <Button
                                variant="ghost"
                                size="sm"
                                className="text-danger"
                                onClick={() => deleteMut.mutate(catId)}
                              >
                                Suppr.
                              </Button>
                            ) : null;
                          })()}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>
          </>
        )}
      </div>
    </Layout>
  );
};

export default BudgetPage;
