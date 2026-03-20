import React, { useState } from "react";
import { PieChart, Lightbulb } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { SpendingDonut } from "@/components/charts/SpendingDonut";
import { useSpending } from "@/hooks/useSpending";
import { useAccounts } from "@/hooks/useAccounts";

const PERIODS = [
  { label: "7 jours", value: 7 },
  { label: "30 jours", value: 30 },
  { label: "90 jours", value: 90 },
];

const SpendingPage: React.FC = () => {
  const [period, setPeriod] = useState(30);
  const { data: accounts } = useAccounts();
  const accountId = accounts?.[0]?.id ?? "";
  const { data: spending, isLoading } = useSpending(accountId, period);

  const fmt = (n: number) =>
    new Intl.NumberFormat("fr-FR", {
      style: "currency",
      currency: "EUR",
      maximumFractionDigits: 0,
    }).format(n);

  return (
    <Layout title="Analyse des dépenses">
      <div className="max-w-5xl mx-auto space-y-6 animate-fade-in">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="page-title">Analyse des dépenses</h1>
            <p className="page-subtitle">
              Répartition et tendances par catégorie
            </p>
          </div>
          <div className="flex gap-2">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                onClick={() => setPeriod(p.value)}
                className={`px-4 py-2 rounded-xl text-sm font-medium transition-all ${
                  period === p.value
                    ? "bg-primary text-white"
                    : "bg-white/[0.04] text-text-secondary border border-white/10 hover:text-white"
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <Loader text="Analyse en cours…" />
        ) : spending ? (
          <>
            {/* Total */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <Card className="stat-card">
                <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
                  Total dépensé{" "}
                  <HelpTooltip text="Somme de toutes les sorties d'argent détectées sur vos comptes Open Banking sur la période sélectionnée." />
                </p>
                <p className="text-3xl font-bold text-white">
                  {fmt(spending.totalSpent)}
                </p>
                <p className="text-text-muted text-xs mt-1">
                  Sur {period} jours
                </p>
              </Card>
              <Card className="lg:col-span-2">
                <CardHeader
                  title="Répartition par catégorie"
                  icon={<PieChart size={18} />}
                  action={
                    <HelpTooltip text="Visualisation de vos dépenses réparties par catégorie sous forme de donut. Les catégories sont détectées automatiquement." />
                  }
                />
                <SpendingDonut data={spending} height={300} />
              </Card>
            </div>

            {/* Category breakdown */}
            <Card>
              <CardHeader
                title="Détail par catégorie"
                action={
                  <HelpTooltip text="Détail chiffré de vos dépenses avec barres de progression pour visualiser le poids de chaque catégorie." />
                }
              />
              <div className="space-y-2">
                {Object.entries(spending.byCategory)
                  .filter(([, v]) => v && v > 0)
                  .sort(([, a], [, b]) => (b ?? 0) - (a ?? 0))
                  .map(([category, amount]) => {
                    const pct =
                      spending.totalSpent > 0
                        ? Math.round(
                            ((amount ?? 0) / spending.totalSpent) * 100,
                          )
                        : 0;
                    return (
                      <div key={category} className="space-y-1.5">
                        <div className="flex items-center justify-between text-sm">
                          <span className="text-text-secondary">
                            {category}
                          </span>
                          <div className="flex items-center gap-3">
                            <span className="text-text-muted text-xs">
                              {pct}%
                            </span>
                            <span className="text-white font-medium tabular-nums">
                              {fmt(amount ?? 0)}
                            </span>
                          </div>
                        </div>
                        <div className="h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary rounded-full transition-all duration-500"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
              </div>
            </Card>

            {/* AI Insights */}
            {spending.insights.length > 0 && (
              <Card>
                <CardHeader
                  title="Recommandations IA"
                  subtitle="Analyse automatique de vos dépenses"
                  icon={<Lightbulb size={18} />}
                  action={
                    <HelpTooltip text="Insights générés par le moteur IA de FlowGuard pour optimiser vos dépenses et améliorer votre santé financière." />
                  }
                />
                <div className="space-y-3">
                  {spending.insights.map((insight, i) => (
                    <div
                      key={i}
                      className="flex items-start gap-3 p-3 bg-primary/5 border border-primary/10 rounded-xl"
                    >
                      <div className="w-6 h-6 rounded-full bg-primary/20 flex items-center justify-center text-primary text-xs font-bold flex-shrink-0">
                        {i + 1}
                      </div>
                      <p className="text-text-secondary text-sm">{insight}</p>
                    </div>
                  ))}
                </div>
              </Card>
            )}
          </>
        ) : (
          <div className="py-20 text-center text-text-muted">
            Connectez votre compte bancaire pour voir l'analyse
          </div>
        )}
      </div>
    </Layout>
  );
};

export default SpendingPage;
