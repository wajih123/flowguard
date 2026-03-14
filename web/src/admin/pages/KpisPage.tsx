import React from "react";
import {
  Download,
  TrendingUp,
  Clock,
  CreditCard,
  Flame,
  Gauge,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { kpisApi } from "@/api/kpis";

interface KpiCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
  info?: string;
  color?: string;
}

const KpiCard: React.FC<KpiCardProps> = ({
  label,
  value,
  icon,
  info,
  color = "text-primary",
}) => (
  <div className="stat-card flex flex-col gap-3">
    <div className="flex items-center justify-between">
      <span className="text-text-secondary text-sm">{label}</span>
      <span className={color}>{icon}</span>
    </div>
    <p className={`text-2xl font-bold text-white`}>{value}</p>
    {info && <p className="text-text-secondary text-xs">{info}</p>}
  </div>
);

const fmt = (n: number | undefined) =>
  n != null
    ? new Intl.NumberFormat("fr-FR", {
        style: "currency",
        currency: "EUR",
        maximumFractionDigits: 0,
      }).format(n)
    : "—";

const fmtDays = (n: number | undefined) =>
  n != null ? `${Math.round(n)} j` : "—";

const AdminKpisPage: React.FC = () => {
  const [exporting, setExporting] = React.useState(false);

  const { data: kpis, isLoading } = useQuery({
    queryKey: ["admin", "kpis"],
    queryFn: () => kpisApi.get(),
    staleTime: 30 * 60 * 1000,
  });

  const handleExportFec = async () => {
    setExporting(true);
    try {
      const blob = await kpisApi.exportFec();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `FEC_export_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } finally {
      setExporting(false);
    }
  };

  return (
    <AdminLayout
      title="KPIs financiers"
      action={
        <Button
          variant="secondary"
          size="sm"
          leftIcon={<Download size={16} />}
          isLoading={exporting}
          onClick={handleExportFec}
        >
          Export FEC
        </Button>
      }
    >
      <div className="max-w-5xl mx-auto space-y-6 animate-fade-in">
        <div>
          <h1 className="text-2xl font-bold text-white">KPIs Financiers</h1>
          <p className="text-text-secondary text-sm mt-1">
            Indicateurs de performance agrégés — plateforme
          </p>
        </div>

        {isLoading ? (
          <Loader />
        ) : kpis ? (
          <>
            <Card>
              <CardHeader
                title="Trésorerie & Liquidité"
                icon={<CreditCard size={18} />}
              />
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
                <KpiCard
                  label="Solde actuel"
                  value={fmt(kpis.currentBalance)}
                  icon={<Gauge size={18} />}
                  color={
                    kpis.currentBalance != null && kpis.currentBalance < 0
                      ? "text-danger"
                      : "text-success"
                  }
                />
                <KpiCard
                  label="BFR"
                  value={fmt(kpis.bfr)}
                  icon={<TrendingUp size={18} />}
                  info="Besoin en fonds de roulement"
                />
                <KpiCard
                  label="Revenus 30j"
                  value={fmt(kpis.totalIncome30d)}
                  icon={<TrendingUp size={18} />}
                  color="text-success"
                />
                <KpiCard
                  label="Dépenses 30j"
                  value={fmt(kpis.totalExpenses30d)}
                  icon={<Flame size={18} />}
                  color="text-danger"
                />
              </div>
            </Card>

            <Card>
              <CardHeader
                title="Délais de paiement"
                icon={<Clock size={18} />}
              />
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                <KpiCard
                  label="DSO"
                  value={fmtDays(kpis.dso)}
                  icon={<Clock size={18} />}
                  info="Délai moyen de recouvrement client"
                  color="text-warning"
                />
                <KpiCard
                  label="DPO"
                  value={fmtDays(kpis.dpo)}
                  icon={<Clock size={18} />}
                  info="Délai moyen de paiement fournisseur"
                  color="text-primary"
                />
              </div>
            </Card>

            <Card>
              <CardHeader title="Survie & Risque" icon={<Flame size={18} />} />
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                <KpiCard
                  label="Burn Rate"
                  value={fmt(kpis.monthlyBurnRate)}
                  icon={<Flame size={18} />}
                  info="Consommation mensuelle de trésorerie"
                  color="text-danger"
                />
                <KpiCard
                  label="Runway"
                  value={fmtDays(kpis.runwayDays)}
                  icon={<Gauge size={18} />}
                  info="Nombre de jours avant épuisement"
                  color={
                    kpis.runwayDays != null && kpis.runwayDays < 30
                      ? "text-danger"
                      : "text-success"
                  }
                />
              </div>
            </Card>
          </>
        ) : (
          <p className="text-text-secondary">Aucune donnée disponible.</p>
        )}
      </div>
    </AdminLayout>
  );
};

export default AdminKpisPage;
