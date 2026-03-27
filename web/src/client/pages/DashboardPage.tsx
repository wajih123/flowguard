import React, { useState } from "react";
import { Link } from "react-router-dom";
import {
  Zap,
  Activity,
  PieChart,
  Building2,
  FileDown,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  CreditCard,
  CheckCircle,
  X,
  ArrowRightLeft,
} from "lucide-react";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";
import { ResponsiveContainer, PieChart as RechartsPie, Cell } from "recharts";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { AlertBanner } from "@/components/ui/AlertBanner";
import { BalanceCard } from "@/components/dashboard/BalanceCard";
import { HealthScoreCard } from "@/components/dashboard/HealthScoreCard";
import { NextEventCard } from "@/components/dashboard/NextEventCard";
import { ForecastChart } from "@/components/dashboard/ForecastChart";
import { TransactionList } from "@/components/dashboard/TransactionList";
import { ReserveWidget } from "@/components/dashboard/ReserveWidget";
import { AlertsList } from "@/components/dashboard/AlertsList";
import {
  useDashboard,
  useDashboardTransactions,
  useEnrichedDashboard,
  useSpendingByCategory,
  useSweepSuggestions,
} from "@/hooks/useDashboard";
import { usePredictions } from "@/hooks/usePredictions";
import { useAuthStore } from "@/store/authStore";
import { useQuery } from "@tanstack/react-query";
import { clientStatsApi } from "@/api/clientStats";
import apiClient from "@/api/client";

const DashboardPage: React.FC = () => {
  const { user } = useAuthStore();
  const { data: dashboard, isLoading: dashLoading } = useDashboard();
  const { data: transactions, isLoading: txLoading } =
    useDashboardTransactions();
  const { data: prediction, isLoading: predLoading } = usePredictions(
    dashboard?.account?.id,
  );
  const { data: enriched } = useEnrichedDashboard();
  const { data: spending } = useSpendingByCategory(1);
  const {
    data: sweepSuggestions,
    accept: acceptSweep,
    dismiss: dismissSweep,
  } = useSweepSuggestions();

  const [alertDismissed, setAlertDismissed] = useState(false);
  const [concentrationDismissed, setConcentrationDismissed] = useState(false);
  const [reserveOpen, setReserveOpen] = useState(false);
  const [pdfLoading, setPdfLoading] = useState(false);

  const { data: clientStats } = useQuery({
    queryKey: ["client-stats"],
    queryFn: clientStatsApi.list,
    staleTime: 10 * 60 * 1000,
    enabled: user?.role === "ROLE_BUSINESS",
  });
  const concentrationRiskClient = clientStats?.find(
    (c) => c.isConcentrationRisk,
  );

  const downloadReport = async () => {
    setPdfLoading(true);
    try {
      const response = await apiClient.get("/api/reports/financial", {
        responseType: "blob",
      });
      const url = globalThis.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.download = `rapport-financier-${new Date().toISOString().split("T")[0]}.pdf`;
      link.click();
      globalThis.URL.revokeObjectURL(url);
    } finally {
      setPdfLoading(false);
    }
  };

  const greeting = () => {
    const h = new Date().getHours();
    if (h < 12) return "Bonjour";
    if (h < 18) return "Bon après-midi";
    return "Bonsoir";
  };

  const showAlert = !alertDismissed && !!dashboard?.hasHighAlert;

  return (
    <Layout>
      <div className="max-w-6xl mx-auto space-y-5 animate-slide-up">
        {/* ── 1. ALERT BANNER ──────────────────────────────────────────── */}
        {showAlert && (
          <AlertBanner
            severity="HIGH"
            title="Déficit prévu dans les prochains jours"
            message={
              dashboard?.highAlertMessage ??
              "Déficit de trésorerie prévu. Activez la Réserve pour le couvrir."
            }
            ctaLabel="⚡ Activer la Réserve"
            onCta={() => setReserveOpen(true)}
            onDismiss={() => setAlertDismissed(true)}
          />
        )}

        {/* ── CONCENTRATION RISK BANNER ───────────────────────────────── */}
        {!concentrationDismissed && concentrationRiskClient && (
          <div className="flex items-start justify-between gap-3 p-4 rounded-xl border border-warning/25 bg-warning/10">
            <div className="flex items-start gap-3">
              <AlertTriangle
                size={18}
                className="text-warning mt-0.5 shrink-0"
              />
              <div>
                <p className="text-white text-sm font-semibold">
                  Risque de concentration client
                </p>
                <p className="text-text-secondary text-xs mt-0.5">
                  <strong>{concentrationRiskClient.clientName}</strong>{" "}
                  représente {Math.round(concentrationRiskClient.revenueShare)}%
                  de votre CA. Diversifiez votre portefeuille pour réduire ce
                  risque.
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <Link
                to="/clients"
                className="text-xs text-warning font-medium hover:text-white transition"
              >
                Voir l’analyse
              </Link>
              <button
                onClick={() => setConcentrationDismissed(true)}
                className="text-text-muted hover:text-white text-sm p-1 rounded transition"
                aria-label="Fermer"
              >
                ×
              </button>
            </div>
          </div>
        )}

        {/* ── 2. HEADER ────────────────────────────────────────────────── */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              {greeting()}
              {user?.firstName ? `, ${user.firstName}` : ""} 👋
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              {format(new Date(), "EEEE d MMMM yyyy", { locale: fr })}
            </p>
            {user?.role &&
              user.role !== "ROLE_ADMIN" &&
              user.role !== "ROLE_SUPER_ADMIN" && (
                <span className="inline-block mt-1 text-2xs text-primary bg-primary/10 border border-primary/20 rounded-full px-2 py-0.5">
                  {user.role === "ROLE_BUSINESS" ? "Pro" : "Particulier"}
                </span>
              )}
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={downloadReport}
            disabled={pdfLoading}
            className="gap-1.5 text-text-muted hover:text-white"
          >
            <FileDown size={15} />
            {pdfLoading ? "Génération…" : "Rapport PDF"}
          </Button>
        </div>

        {/* ── 3. TOP ROW: Balance | Health | Next Event ────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <BalanceCard
            dashboard={dashboard}
            isLoading={dashLoading}
            onRefresh={() => {}}
          />
          <HealthScoreCard
            score={dashboard?.healthScore}
            label={dashboard?.healthLabel}
            isLoading={dashLoading}
          />
          <NextEventCard prediction={prediction} isLoading={predLoading} />
        </div>

        {/* ── 4. FORECAST CHART — full width ───────────────────────────── */}
        <ForecastChart prediction={prediction} isLoading={predLoading} />

        {/* ── 4B. SMART FEATURES ROW 1 ─────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Overdraft Risk */}
          {enriched?.overdraftRisk && (
            <Card hover padding="md">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-white font-semibold text-sm">
                  Solde dans 30 jours
                </h3>
                <div
                  className={
                    enriched.overdraftRisk.level === "HIGH"
                      ? "text-danger"
                      : enriched.overdraftRisk.level === "MEDIUM"
                        ? "text-warning"
                        : enriched.overdraftRisk.level === "NONE"
                          ? "text-success"
                          : "text-info"
                  }
                >
                  {enriched.overdraftRisk.level === "HIGH" && (
                    <AlertTriangle size={16} />
                  )}
                  {enriched.overdraftRisk.level === "MEDIUM" && (
                    <TrendingDown size={16} />
                  )}
                  {enriched.overdraftRisk.level === "NONE" && (
                    <CheckCircle size={16} />
                  )}
                </div>
              </div>
              <p className="text-2xl font-numeric font-bold text-white">
                {new Intl.NumberFormat("fr-FR", {
                  style: "currency",
                  currency: "EUR",
                }).format(enriched.overdraftRisk.projectedBalance)}
              </p>
              <p className="text-xs text-text-muted mt-1">
                Jour{" "}
                {format(parseISO(enriched.overdraftRisk.horizonDate), "d MMM", {
                  locale: fr,
                })}
              </p>
            </Card>
          )}

          {/* Monthly Subscriptions */}
          {enriched && (
            <Link to="/subscriptions" className="block">
              <Card hover padding="md" className="h-full">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-white font-semibold text-sm">
                    Abonnements
                  </h3>
                  <CreditCard size={16} className="text-primary" />
                </div>
                <p className="text-2xl font-numeric font-bold text-white">
                  {new Intl.NumberFormat("fr-FR", {
                    style: "currency",
                    currency: "EUR",
                  }).format(enriched.monthlySubscriptionsCost)}
                </p>
                <p className="text-xs text-text-muted mt-1">/mois</p>
              </Card>
            </Link>
          )}

          {/* Last Month Income */}
          {enriched && (
            <Card padding="md">
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-white font-semibold text-sm">
                  Revenus mois écoulé
                </h3>
                <TrendingUp size={16} className="text-success" />
              </div>
              <p className="text-2xl font-numeric font-bold text-success">
                {new Intl.NumberFormat("fr-FR", {
                  style: "currency",
                  currency: "EUR",
                }).format(enriched.lastMonthIncome)}
              </p>
              <p className="text-xs text-text-muted mt-1">
                Sortie:{" "}
                {new Intl.NumberFormat("fr-FR", {
                  style: "currency",
                  currency: "EUR",
                }).format(enriched.lastMonthSpend)}
              </p>
            </Card>
          )}

          {/* Savings / Deficit */}
          {enriched &&
            (() => {
              const isDeficit = enriched.lastMonthSavings < 0;
              const savingsRate =
                enriched.lastMonthIncome > 0
                  ? Math.max(
                      0,
                      (enriched.lastMonthSavings / enriched.lastMonthIncome) *
                        100,
                    )
                  : 0;
              return (
                <Card padding="md">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="text-white font-semibold text-sm">
                      {isDeficit ? "Déficit" : "Épargne"}
                    </h3>
                    {isDeficit ? (
                      <TrendingDown size={16} className="text-red-400" />
                    ) : (
                      <TrendingUp size={16} className="text-cyan-400" />
                    )}
                  </div>
                  <p
                    className={`text-2xl font-numeric font-bold ${
                      isDeficit ? "text-red-400" : "text-cyan-400"
                    }`}
                  >
                    {new Intl.NumberFormat("fr-FR", {
                      style: "currency",
                      currency: "EUR",
                    }).format(Math.abs(enriched.lastMonthSavings))}
                  </p>
                  <p className="text-xs text-text-muted mt-1">
                    {isDeficit
                      ? "Dépenses supérieures aux revenus"
                      : enriched.lastMonthIncome > 0
                        ? savingsRate.toFixed(1) + "%"
                        : "N/A"}
                  </p>
                </Card>
              );
            })()}
        </div>

        {/* ── 4C. UPCOMING DEBITS & SWEEP SUGGESTIONS ────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Upcoming Debits in 7 days */}
          {enriched && enriched.upcomingDebits?.length > 0 && (
            <Card padding="md">
              <h3 className="text-white font-semibold text-sm mb-3">
                Débits attendus (7 jours)
              </h3>
              <div className="space-y-2">
                {enriched.upcomingDebits.map((debit, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-2 bg-white/[0.02] rounded border border-white/[0.04]"
                  >
                    <span className="text-text-muted text-xs">
                      {debit.label}
                    </span>
                    <span className="text-white font-numeric font-semibold">
                      {new Intl.NumberFormat("fr-FR", {
                        style: "currency",
                        currency: "EUR",
                      }).format(debit.amount)}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* Sweep Suggestions */}
          {sweepSuggestions && sweepSuggestions.length > 0 && (
            <Card padding="md">
              <h3 className="text-white font-semibold text-sm mb-3">
                Suggestions d'optimisation
              </h3>
              <div className="space-y-2">
                {sweepSuggestions.slice(0, 3).map((suggestion) => (
                  <div
                    key={suggestion.id}
                    className="flex items-center justify-between p-2 bg-primary/5 rounded border border-primary/20"
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-white text-xs font-medium truncate">
                        {suggestion.reason}
                      </p>
                      <p className="text-text-muted text-xs">
                        {new Intl.NumberFormat("fr-FR", {
                          style: "currency",
                          currency: "EUR",
                        }).format(suggestion.suggestedAmount)}
                      </p>
                    </div>
                    <div className="flex items-center gap-1 shrink-0 ml-2">
                      <button
                        onClick={() => acceptSweep.mutate(suggestion.id)}
                        className="p-1 rounded hover:bg-success/20 text-success transition"
                        title="Accepter"
                      >
                        <CheckCircle size={14} />
                      </button>
                      <button
                        onClick={() => dismissSweep.mutate(suggestion.id)}
                        className="p-1 rounded hover:bg-danger/20 text-danger transition"
                        title="Rejeter"
                      >
                        <X size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        {/* ── 4D. SPENDING BY CATEGORY ──────────────────────────────────────── */}
        {spending && spending.length > 0 && (
          <Card padding="md">
            <h3 className="text-white font-semibold text-sm mb-4">
              Répartition dépenses (30 jours)
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsPie
                    data={spending}
                    cx="50%"
                    cy="50%"
                    innerRadius={40}
                    outerRadius={80}
                    dataKey="amount"
                  >
                    {spending.map((item, index) => (
                      <Cell
                        key={`status-cell-${item.category || index}`}
                        fill={
                          [
                            "#00D9FF",
                            "#1E40AF",
                            "#8B5CF6",
                            "#EC4899",
                            "#F59E0B",
                            "#10B981",
                          ][index % 6]
                        }
                      />
                    ))}
                  </RechartsPie>
                </ResponsiveContainer>
              </div>
              <div className="md:col-span-2 space-y-2">
                {spending.map((cat, i) => (
                  <div
                    key={`action-${i}`}
                    className="flex items-center justify-between"
                  >
                    <div className="flex items-center gap-2">
                      <div
                        className="w-2 h-2 rounded-full"
                        style={{
                          backgroundColor: [
                            "#00D9FF",
                            "#1E40AF",
                            "#8B5CF6",
                            "#EC4899",
                            "#F59E0B",
                            "#10B981",
                          ][i % 6],
                        }}
                      />
                      <span className="text-text-muted text-sm">
                        {cat.category}
                      </span>
                    </div>
                    <span className="text-white font-numeric font-semibold text-sm">
                      {new Intl.NumberFormat("fr-FR", {
                        style: "currency",
                        currency: "EUR",
                      }).format(cat.amount)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        )}

        {/* ── 5. BOTTOM ROW: Transactions | Reserve ────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          <div className="md:col-span-3">
            <TransactionList
              transactions={transactions}
              isLoading={txLoading}
            />
          </div>
          <div className="md:col-span-2">
            <ReserveWidget
              dashboard={dashboard}
              isOpen={reserveOpen}
              onClose={() => setReserveOpen(false)}
              onActivate={() => setReserveOpen(true)}
            />
          </div>
        </div>

        {/* ── 6. ALERTS ────────────────────────────────────────────────── */}
        <AlertsList limit={5} />

        {/* ── 7. QUICK ACTIONS ─────────────────────────────────────────── */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {(
            [
              {
                label: "Réserve",
                desc: "Financement instantané",
                to: "/flash-credit",
                icon: <Zap size={20} />,
                color: "text-primary",
                roles: ["ROLE_USER", "ROLE_BUSINESS"] as string[],
              },
              {
                label: "Analyses",
                desc: "Répartition dépenses",
                to: "/spending",
                icon: <PieChart size={20} />,
                color: "text-purple",
                roles: null,
              },
              {
                label: "Scénarios",
                desc: "Simuler un impact",
                to: "/scenarios",
                icon: <Activity size={20} />,
                color: "text-warning",
                roles: ["ROLE_BUSINESS"] as string[],
              },
              {
                label: "Transactions",
                desc: "Historique complet",
                to: "/transactions",
                icon: <ArrowRightLeft size={20} />,
                color: "text-success",
                roles: null,
              },
              {
                label: "Ma banque",
                desc: "Gérer la connexion",
                to: "/bank-connect",
                icon: <Building2 size={20} />,
                color: "text-cyan-400",
                roles: null,
              },
            ] as const
          )
            .filter(
              (action) =>
                !action.roles ||
                (user?.role != null &&
                  (action.roles as string[]).includes(user.role)),
            )
            .map((action) => (
              <Link key={action.to} to={action.to}>
                <Card hover padding="md" className="h-full">
                  <div className={`mb-3 ${action.color}`}>{action.icon}</div>
                  <p
                    className="text-white font-medium text-sm"
                    style={{ fontFamily: "var(--font-display)" }}
                  >
                    {action.label}
                  </p>
                  <p className="text-text-muted text-xs mt-0.5">
                    {action.desc}
                  </p>
                </Card>
              </Link>
            ))}
        </div>
      </div>
    </Layout>
  );
};

export default DashboardPage;
