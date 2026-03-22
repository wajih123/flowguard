import React, { useState } from "react";
import { Link } from "react-router-dom";
import {
  Zap,
  Activity,
  ArrowRightLeft,
  PieChart,
  Building2,
  FileDown,
  AlertTriangle,
} from "lucide-react";
import { format } from "date-fns";
import { fr } from "date-fns/locale";
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
import { useDashboard, useDashboardTransactions } from "@/hooks/useDashboard";
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
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.download = `rapport-financier-${new Date().toISOString().split("T")[0]}.pdf`;
      link.click();
      window.URL.revokeObjectURL(url);
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
            {user?.role && (
              <span className="inline-block mt-1 text-2xs text-primary bg-primary/10 border border-primary/20 rounded-full px-2 py-0.5">
                {user.role.replace("ROLE_", "")}
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
