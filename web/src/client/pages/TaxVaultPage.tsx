import React from "react";
import { ShieldCheck, RefreshCw, AlertTriangle, Info } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { taxVaultApi } from "@/api/taxVault";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const pct = (n: number, total: number) =>
  total > 0 ? Math.round((n / total) * 100) : 0;

interface ReserveRowProps {
  label: string;
  amount: number;
  color: string;
  tooltip: string;
}

const ReserveRow: React.FC<ReserveRowProps> = ({
  label,
  amount,
  color,
  tooltip,
}) => (
  <div className="flex items-center justify-between gap-4 py-3 border-b border-white/[0.05] last:border-0">
    <div className="flex items-center gap-2">
      <div className={`w-2.5 h-2.5 rounded-full ${color}`} />
      <span className="text-text-secondary text-sm">{label}</span>
      <HelpTooltip text={tooltip} />
    </div>
    <span className="font-numeric font-semibold text-white">{fmt(amount)}</span>
  </div>
);

const TaxVaultPage: React.FC = () => {
  const { data, isLoading, refetch } = useQuery({
    queryKey: ["tax-vault"],
    queryFn: taxVaultApi.get,
    staleTime: 5 * 60 * 1000,
  });

  return (
    <Layout title="Coffre fiscal">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Coffre fiscal
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Provisions automatiques TVA · URSSAF · IS — solde réellement
              disponible
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

        {isLoading ? (
          <Loader text="Calcul des provisions…" />
        ) : !data ? (
          <Card>
            <div className="flex flex-col items-center py-10 gap-3 text-text-muted">
              <ShieldCheck size={32} className="opacity-30" />
              <p className="text-sm">Aucune donnée disponible</p>
              <p className="text-xs">
                Connectez un compte bancaire pour commencer
              </p>
            </div>
          </Card>
        ) : (
          <>
            {/* Spendable balance hero */}
            <Card padding="lg">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <p className="text-text-muted text-xs mb-1 flex items-center gap-1.5">
                    <ShieldCheck size={12} className="text-success" />
                    Solde réellement disponible
                  </p>
                  <p className="font-numeric text-3xl font-bold text-success">
                    {fmt(data.spendableBalance)}
                  </p>
                  <p className="text-text-muted text-xs mt-1">
                    Solde brut {fmt(data.currentBalance)} − provisions{" "}
                    {fmt(data.totalReserved)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-text-muted text-xs mb-1">
                    Revenus encaissés (30j)
                  </p>
                  <p className="font-numeric text-xl font-bold text-white">
                    {fmt(data.grossIncomeLast30Days)}
                  </p>
                </div>
              </div>

              {/* Progress bar */}
              {data.currentBalance > 0 && (
                <div className="mt-4">
                  <div className="flex justify-between text-xs text-text-muted mb-1.5">
                    <span>
                      Réservé ({pct(data.totalReserved, data.currentBalance)}%)
                    </span>
                    <span>
                      Disponible (
                      {pct(data.spendableBalance, data.currentBalance)}%)
                    </span>
                  </div>
                  <div className="h-2 rounded-full bg-white/[0.08] overflow-hidden">
                    <div
                      className="h-full rounded-full bg-danger/70 transition-all duration-500"
                      style={{
                        width: `${Math.min(pct(data.totalReserved, data.currentBalance), 100)}%`,
                      }}
                    />
                  </div>
                </div>
              )}
            </Card>

            {/* Breakdown */}
            <Card padding="none">
              <CardHeader
                title="Détail des provisions"
                helpTooltip={
                  <HelpTooltip text="Montants calculés sur les 30 derniers jours de revenus. TVA 20%, URSSAF 22.1% (BIC services), IS 15% (PME)." />
                }
              />
              <div className="px-4 pb-2">
                <ReserveRow
                  label="TVA collectée (20%)"
                  amount={data.tvaReserve}
                  color="bg-warning"
                  tooltip="TVA à reverser à l'État sur vos encaissements TTC."
                />
                <ReserveRow
                  label="URSSAF / cotisations (22.1%)"
                  amount={data.urssafReserve}
                  color="bg-primary"
                  tooltip="Cotisations sociales estimées — régime micro-BIC services ou SAS."
                />
                <ReserveRow
                  label="Impôt sur les Sociétés (15%)"
                  amount={data.isReserve}
                  color="bg-info"
                  tooltip="IS réduit PME (taux 15% sur 42 500€ de bénéfice). Provision conservative."
                />
                <div className="flex items-center justify-between py-3">
                  <span className="text-text-secondary text-sm font-semibold">
                    Total à provisionner
                  </span>
                  <span className="font-numeric font-bold text-white">
                    {fmt(data.totalReserved)}
                  </span>
                </div>
              </div>
            </Card>

            {/* Warning if spendable is low */}
            {data.spendableBalance < data.currentBalance * 0.3 && (
              <div className="flex items-start gap-3 p-4 rounded-xl bg-warning/10 border border-warning/20">
                <AlertTriangle
                  size={16}
                  className="text-warning mt-0.5 flex-shrink-0"
                />
                <div>
                  <p className="text-warning text-sm font-semibold">
                    Solde disponible faible
                  </p>
                  <p className="text-text-secondary text-xs mt-0.5">
                    Moins de 30% de votre solde brut est librement disponible
                    après provisions fiscales. Vérifiez vos encaissements à
                    venir.
                  </p>
                </div>
              </div>
            )}

            <div className="flex items-start gap-2 text-text-muted text-xs px-1">
              <Info size={12} className="mt-0.5 flex-shrink-0" />
              <p>
                Ces provisions sont des estimations basées sur vos revenus des
                30 derniers jours. Consultez votre expert-comptable pour les
                montants exacts.
              </p>
            </div>
          </>
        )}
      </div>
    </Layout>
  );
};

export default TaxVaultPage;
