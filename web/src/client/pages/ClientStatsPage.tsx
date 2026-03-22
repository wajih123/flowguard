import React, { useMemo } from "react";
import {
  Users,
  AlertTriangle,
  Clock,
  TrendingUp,
  RefreshCw,
  CheckCircle,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { clientStatsApi } from "@/api/clientStats";
import type { ClientStats } from "@/api/clientStats";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

const pct = (n: number) => `${Math.round(n * 10) / 10}%`;

const PaymentBadge: React.FC<{ days: number }> = ({ days }) => {
  const color =
    days === 0
      ? "text-text-muted"
      : days <= 15
        ? "text-success"
        : days <= 30
          ? "text-warning"
          : "text-danger";
  return (
    <span className={`flex items-center gap-1 text-xs font-medium ${color}`}>
      <Clock size={11} />
      {days === 0 ? "—" : `${days}j`}
    </span>
  );
};

const ShareBar: React.FC<{ share: number; risk: boolean }> = ({
  share,
  risk,
}) => (
  <div className="flex items-center gap-2 mt-1">
    <div className="flex-1 h-1.5 bg-white/[0.07] rounded-full overflow-hidden">
      <div
        className={`h-full rounded-full transition-all ${risk ? "bg-danger" : "bg-primary"}`}
        style={{ width: `${Math.min(share, 100)}%` }}
      />
    </div>
    <span
      className={`text-xs font-numeric font-medium ${risk ? "text-danger" : "text-text-muted"}`}
    >
      {pct(share)}
    </span>
  </div>
);

const ClientRow: React.FC<{ client: ClientStats }> = ({ client }) => (
  <div className="flex flex-col gap-1 px-4 py-3 border-b border-white/[0.05] last:border-0">
    <div className="flex items-center justify-between gap-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-white text-sm font-medium truncate">
            {client.clientName}
          </p>
          {client.isConcentrationRisk && (
            <span className="flex items-center gap-1 px-1.5 py-0.5 bg-danger/15 text-danger text-2xs font-semibold rounded">
              <AlertTriangle size={9} />
              Risque
            </span>
          )}
        </div>
        <p className="text-text-muted text-xs truncate">{client.clientEmail}</p>
      </div>
      <div className="text-right shrink-0">
        <p className="font-numeric text-white text-sm font-semibold">
          {fmt(client.totalRevenue)}
        </p>
        {client.outstandingAmount > 0 && (
          <p className="text-text-muted text-xs">
            +{fmt(client.outstandingAmount)} en attente
          </p>
        )}
      </div>
    </div>
    <div className="flex items-center gap-4">
      <ShareBar share={client.revenueShare} risk={client.isConcentrationRisk} />
      <div className="flex items-center gap-3 shrink-0 text-text-muted text-xs">
        <span>{client.invoiceCount} fact.</span>
        <PaymentBadge days={client.avgPaymentDays} />
        {client.avgPaymentDays !== client.predictedPaymentDays &&
          client.predictedPaymentDays > 0 && (
            <span className="text-warning">
              → {client.predictedPaymentDays}j
            </span>
          )}
      </div>
    </div>
  </div>
);

const ClientStatsPage: React.FC = () => {
  const {
    data: clients,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["client-stats"],
    queryFn: clientStatsApi.list,
    staleTime: 5 * 60 * 1000,
  });

  const summary = useMemo(() => {
    if (!clients || clients.length === 0) return null;
    const atRisk = clients.filter((c) => c.isConcentrationRisk);
    const totalClients = clients.length;
    const avgDelay = Math.round(
      clients.reduce((s, c) => s + c.avgPaymentDays, 0) / totalClients,
    );
    const topShare = clients[0]?.revenueShare ?? 0;
    return { atRisk, totalClients, avgDelay, topShare };
  }, [clients]);

  return (
    <Layout title="Analyse clients">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Analyse clients
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Rentabilité · délais de paiement · risque de concentration
            </p>
          </div>
          <button
            onClick={() => refetch()}
            className="p-2 rounded-lg text-text-muted hover:text-white hover:bg-white/[0.05] transition"
            title="Actualiser"
          >
            <RefreshCw size={16} />
          </button>
        </div>

        {/* KPI row */}
        {summary && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card padding="md">
              <p className="text-text-muted text-xs mb-1">Clients actifs</p>
              <p className="font-numeric text-white text-xl font-bold">
                {summary.totalClients}
              </p>
            </Card>
            <Card padding="md">
              <p className="text-text-muted text-xs mb-1">
                Délai moyen encaissement
              </p>
              <p
                className={`font-numeric text-xl font-bold ${
                  summary.avgDelay <= 15
                    ? "text-success"
                    : summary.avgDelay <= 30
                      ? "text-warning"
                      : "text-danger"
                }`}
              >
                {summary.avgDelay === 0 ? "—" : `${summary.avgDelay}j`}
              </p>
            </Card>
            <Card padding="md">
              <p className="text-text-muted text-xs mb-1">Concentration max</p>
              <p
                className={`font-numeric text-xl font-bold ${
                  summary.topShare >= 40 ? "text-danger" : "text-white"
                }`}
              >
                {pct(summary.topShare)}
              </p>
              {summary.atRisk.length > 0 && (
                <p className="text-danger text-xs mt-0.5">
                  {summary.atRisk.length} client(s) à risque
                </p>
              )}
            </Card>
          </div>
        )}

        {/* Concentration risk banner */}
        {summary && summary.atRisk.length > 0 && (
          <div className="flex items-start gap-3 p-4 rounded-xl border border-danger/25 bg-danger/10">
            <AlertTriangle size={18} className="text-danger mt-0.5 shrink-0" />
            <div>
              <p className="text-white text-sm font-semibold">
                Risque de concentration détecté
              </p>
              <p className="text-text-secondary text-xs mt-0.5">
                {summary.atRisk.map((c) => c.clientName).join(", ")} représente
                {summary.atRisk.length > 1 ? "nt" : ""} plus de 40% de votre CA.
                Diversifiez votre portefeuille clients.
              </p>
            </div>
          </div>
        )}

        {/* Client list */}
        <Card padding="none">
          <CardHeader
            title="Clients par chiffre d'affaires"
            helpTooltip={
              <HelpTooltip text="Calculé sur toutes les factures payées. Les délais de paiement incluent une projection basée sur l'historique." />
            }
          />
          {isLoading ? (
            <div className="px-4 pb-4">
              <Loader text="Chargement de l'analyse…" />
            </div>
          ) : !clients || clients.length === 0 ? (
            <div className="flex flex-col items-center py-12 gap-3 text-text-muted px-4">
              <Users size={32} className="opacity-30" />
              <p className="text-sm">Aucun client trouvé</p>
              <p className="text-xs text-center">
                Créez des factures avec un nom de client pour voir l'analyse
              </p>
            </div>
          ) : (
            <div>
              {/* Legend row */}
              <div className="flex items-center justify-between gap-3 px-4 py-2 border-b border-white/[0.05]">
                <div className="flex items-center gap-1 text-text-muted text-xs">
                  <TrendingUp size={11} />
                  <span>CA encaissé</span>
                </div>
                <div className="flex items-center gap-4 text-text-muted text-xs">
                  <span>Part CA</span>
                  <span>Délai réel</span>
                </div>
              </div>
              {clients.map((client) => (
                <ClientRow key={client.clientName} client={client} />
              ))}
            </div>
          )}
        </Card>

        {/* Info note */}
        <p className="text-text-muted text-xs text-center flex items-center justify-center gap-1.5">
          <CheckCircle size={11} /> Les délais de paiement sont calculés sur les
          factures réglées. Les projections se basent sur votre historique.
        </p>
      </div>
    </Layout>
  );
};

export default ClientStatsPage;
