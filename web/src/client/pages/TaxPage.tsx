import React, { useState } from "react";
import {
  Calendar,
  CheckCircle,
  Clock,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { taxApi } from "@/api/tax";
import type { TaxEstimate } from "@/api/tax";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const TAX_LABELS: Record<string, string> = {
  TVA: "TVA collectée",
  URSSAF: "Cotisations URSSAF",
  IS: "Impôt sur les sociétés",
  IR: "Impôt sur le revenu",
  CFE: "CFE (Cotisation Foncière)",
};

const TAX_COLORS: Record<string, string> = {
  TVA: "text-primary",
  URSSAF: "text-warning",
  IS: "text-purple-400",
  IR: "text-yellow-400",
  CFE: "text-orange-400",
};

const TAX_BG: Record<string, string> = {
  TVA: "border-primary/30 bg-primary/5",
  URSSAF: "border-warning/30 bg-warning/5",
  IS: "border-purple-400/30 bg-purple-400/5",
  IR: "border-yellow-400/30 bg-yellow-400/5",
  CFE: "border-orange-400/30 bg-orange-400/5",
};

const TaxCard: React.FC<{
  tax: TaxEstimate;
  onMarkPaid: () => void;
}> = ({ tax, onMarkPaid }) => {
  const urgency =
    !tax.isPaid && tax.daysUntilDue !== null && tax.daysUntilDue <= 7;
  const overdue =
    !tax.isPaid && tax.daysUntilDue !== null && tax.daysUntilDue < 0;
  return (
    <div
      className={`rounded-xl border p-4 ${TAX_BG[tax.taxType] ?? "border-white/10 bg-white/[0.03]"} transition`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <p
              className={`font-semibold ${TAX_COLORS[tax.taxType] ?? "text-white"}`}
            >
              {TAX_LABELS[tax.taxType] ?? tax.taxType}
            </p>
            <span className="text-xs text-text-muted bg-white/[0.06] px-2 py-0.5 rounded-full">
              {tax.periodLabel}
            </span>
            {tax.isPaid && (
              <span className="text-xs text-success flex items-center gap-0.5">
                <CheckCircle size={11} /> Payé
              </span>
            )}
            {overdue && (
              <span className="text-xs text-danger flex items-center gap-0.5">
                <AlertTriangle size={11} /> En retard
              </span>
            )}
            {urgency && !overdue && (
              <span className="text-xs text-warning flex items-center gap-0.5">
                <Clock size={11} /> Urgent
              </span>
            )}
          </div>
          <p className="text-text-muted text-xs">
            Échéance :{" "}
            {format(parseISO(tax.dueDate), "d MMMM yyyy", { locale: fr })}
            {tax.daysUntilDue !== null && !tax.isPaid && (
              <span
                className={`ml-2 ${overdue ? "text-danger" : urgency ? "text-warning" : "text-text-muted"}`}
              >
                (
                {overdue
                  ? `${Math.abs(tax.daysUntilDue)}j de retard`
                  : `dans ${tax.daysUntilDue}j`}
                )
              </span>
            )}
          </p>
        </div>
        <div className="text-right shrink-0">
          <p className="text-xl font-bold font-numeric text-white">
            {fmt(tax.estimatedAmount)}
          </p>
          {!tax.isPaid && (
            <Button
              size="sm"
              variant="ghost"
              className="mt-2 text-success text-xs"
              onClick={onMarkPaid}
            >
              Marquer payé
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

const TaxPage: React.FC = () => {
  const [showAll, setShowAll] = useState(false);
  const qc = useQueryClient();

  const { data: allTaxes, isLoading } = useQuery({
    queryKey: ["taxes"],
    queryFn: taxApi.getAll,
  });

  const { data: upcoming } = useQuery({
    queryKey: ["taxes-upcoming"],
    queryFn: taxApi.getUpcoming,
  });

  const regenMut = useMutation({
    mutationFn: taxApi.regenerate,
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["taxes", "taxes-upcoming"] }),
  });

  const paidMut = useMutation({
    mutationFn: taxApi.markPaid,
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["taxes", "taxes-upcoming"] }),
  });

  const displayed = showAll ? allTaxes : upcoming;
  const totalUnpaid =
    allTaxes
      ?.filter((t) => !t.isPaid)
      .reduce((s, t) => s + t.estimatedAmount, 0) ?? 0;
  const nextDeadline = upcoming?.[0];

  return (
    <Layout title="Fiscalité">
      <div className="max-w-4xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="page-title">Calendrier fiscal</h1>
            <p className="page-subtitle">
              Anticipez vos obligations URSSAF, TVA et impôts
            </p>
          </div>
          <Button
            variant="ghost"
            className="gap-2"
            onClick={() => regenMut.mutate()}
            disabled={regenMut.isPending}
          >
            <RefreshCw
              size={15}
              className={regenMut.isPending ? "animate-spin" : ""}
            />
            Recalculer
          </Button>
        </div>

        {/* KPIs */}
        <div className="grid grid-cols-3 gap-4">
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              À payer
            </p>
            <p className="text-2xl font-bold font-numeric text-warning">
              {fmt(totalUnpaid)}
            </p>
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              Prochaine échéance
            </p>
            {nextDeadline ? (
              <>
                <p className="text-white font-semibold text-sm">
                  {TAX_LABELS[nextDeadline.taxType]}
                </p>
                <p className="text-text-muted text-xs">
                  {format(parseISO(nextDeadline.dueDate), "d MMM yyyy", {
                    locale: fr,
                  })}
                  {nextDeadline.daysUntilDue !== null && (
                    <span className="text-warning ml-1">
                      · dans {nextDeadline.daysUntilDue}j
                    </span>
                  )}
                </p>
              </>
            ) : (
              <p className="text-success text-sm">Aucune échéance imminente</p>
            )}
          </Card>
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2">
              Obligations totales
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {allTaxes?.length ?? 0}
            </p>
          </Card>
        </div>

        {isLoading ? (
          <Loader text="Chargement…" />
        ) : (
          <Card>
            <CardHeader
              title={
                showAll ? "Toutes les obligations" : "Prochaines échéances"
              }
              action={
                <button
                  className="text-xs text-primary hover:underline"
                  onClick={() => setShowAll(!showAll)}
                >
                  {showAll ? "Afficher les prochaines" : "Tout afficher"}
                </button>
              }
            />
            {!displayed?.length ? (
              <div className="py-10 text-center text-text-muted">
                <Calendar className="mx-auto mb-3 opacity-30" size={36} />
                <p>Aucune obligation fiscale trouvée.</p>
                <p className="text-xs mt-1">
                  Cliquez sur "Recalculer" pour les générer depuis vos factures.
                </p>
              </div>
            ) : (
              <div className="mt-4 space-y-3">
                {displayed.map((tax) => (
                  <TaxCard
                    key={tax.id}
                    tax={tax}
                    onMarkPaid={() => paidMut.mutate(tax.id)}
                  />
                ))}
              </div>
            )}
          </Card>
        )}
      </div>
    </Layout>
  );
};

export default TaxPage;
