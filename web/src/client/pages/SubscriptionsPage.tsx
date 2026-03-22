import React from "react";
import {
  RefreshCw,
  AlertTriangle,
  CheckCircle,
  TrendingDown,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { subscriptionsApi } from "@/api/subscriptions";
import type { SubscriptionSummary } from "@/api/subscriptions";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const CATEGORY_LABELS: Record<string, string> = {
  ABONNEMENT: "Abonnement",
  TELECOM: "Télécom",
  ENERGIE: "Énergie",
  ASSURANCE: "Assurance",
  LOYER: "Loyer",
  CHARGES_FISCALES: "Charges fiscales",
  FOURNISSEUR: "Fournisseur",
  AUTRE: "Autre",
};

const SubscriptionCard: React.FC<{ sub: SubscriptionSummary }> = ({ sub }) => (
  <div
    className={`flex items-center justify-between gap-4 px-4 py-3.5 border rounded-xl transition
      ${
        sub.isStale
          ? "bg-danger/5 border-danger/20 hover:bg-danger/10"
          : "bg-white/[0.02] border-white/[0.06] hover:bg-white/[0.04]"
      }`}
  >
    <div className="flex-1 min-w-0">
      <div className="flex items-center gap-2 mb-1">
        <p className="font-medium text-white truncate">{sub.label}</p>
        {sub.isStale && (
          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-danger/15 text-danger">
            <AlertTriangle size={10} />
            Inutilisé depuis {sub.monthsSinceLastUse} mois
          </span>
        )}
      </div>
      <p className="text-text-muted text-xs">
        {CATEGORY_LABELS[sub.category] ?? sub.category} ·{" "}
        {sub.occurrencesLast12Months}× sur 12 mois · Dernière utilisation :{" "}
        {format(parseISO(sub.lastUsedDate), "d MMM yyyy", { locale: fr })}
      </p>
    </div>
    <div className="text-right shrink-0">
      <p className="font-numeric text-white font-semibold">
        {fmt(sub.monthlyAmount)}
      </p>
      <p className="text-text-muted text-xs">/mois estimé</p>
    </div>
    <div className="shrink-0">
      {sub.isStale ? (
        <AlertTriangle size={16} className="text-danger" />
      ) : (
        <CheckCircle size={16} className="text-success" />
      )}
    </div>
  </div>
);

const SubscriptionsPage: React.FC = () => {
  const {
    data: subscriptions,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["subscriptions"],
    queryFn: subscriptionsApi.list,
    staleTime: 30 * 60 * 1000,
  });

  const totalMonthly =
    subscriptions?.reduce((s, sub) => s + sub.monthlyAmount, 0) ?? 0;
  const staleCount = subscriptions?.filter((s) => s.isStale).length ?? 0;

  return (
    <Layout title="Audit Abonnements">
      <div className="max-w-4xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Audit des abonnements
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Récurrents détectés automatiquement depuis vos transactions
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
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">Coût mensuel total</p>
            <p className="font-numeric text-white text-xl font-bold">
              {fmt(totalMonthly)}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">Tous abonnements</p>
          </Card>
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">Abonnements détectés</p>
            <p className="font-numeric text-white text-xl font-bold">
              {subscriptions?.length ?? 0}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">
              Transactions récurrentes
            </p>
          </Card>
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">
              Potentiellement inutilisés
            </p>
            <p
              className={`font-numeric text-xl font-bold ${staleCount > 0 ? "text-danger" : "text-success"}`}
            >
              {staleCount}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">
              Inactifs depuis +3 mois
            </p>
          </Card>
        </div>

        {/* Stale subscriptions */}
        {staleCount > 0 && (
          <Card padding="none">
            <CardHeader
              title="À analyser — inutilisés depuis plus de 3 mois"
              helpTooltip={
                <HelpTooltip text="Ces abonnements n'ont pas généré de transaction depuis plus de 3 mois. Vérifiez s'ils sont toujours nécessaires." />
              }
            />
            <div className="px-4 pb-4 space-y-2">
              {isLoading ? (
                <Loader text="Chargement…" />
              ) : (
                subscriptions
                  ?.filter((s) => s.isStale)
                  .map((sub) => <SubscriptionCard key={sub.label} sub={sub} />)
              )}
            </div>
          </Card>
        )}

        {/* Active subscriptions */}
        <Card padding="none">
          <CardHeader
            title="Abonnements actifs"
            helpTooltip={
              <HelpTooltip text="Charges récurrentes actives détectées dans vos transactions au cours des 12 derniers mois." />
            }
          />
          <div className="px-4 pb-4 space-y-2">
            {isLoading ? (
              <Loader text="Chargement…" />
            ) : !subscriptions ||
              subscriptions.filter((s) => !s.isStale).length === 0 ? (
              <div className="flex flex-col items-center py-8 text-text-muted gap-2">
                <TrendingDown size={28} className="opacity-40" />
                <p className="text-sm">Aucun abonnement actif détecté</p>
                <p className="text-xs">
                  Les transactions récurrentes apparaîtront ici
                </p>
              </div>
            ) : (
              subscriptions
                ?.filter((s) => !s.isStale)
                .map((sub) => <SubscriptionCard key={sub.label} sub={sub} />)
            )}
          </div>
        </Card>
      </div>
    </Layout>
  );
};

export default SubscriptionsPage;
