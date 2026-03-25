import React from "react";
import {
  CreditCard,
  TrendingDown,
  Calendar,
  AlertTriangle,
  RefreshCw,
  CheckCircle,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { bnplApi, loansApi } from "@/api/debt";
import type { BnplInstallment, LoanAmortization } from "@/api/debt";
import { format, parseISO, differenceInDays } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const BnplCard: React.FC<{ installment: BnplInstallment }> = ({
  installment,
}) => {
  const progress =
    (installment.installmentsPaid / installment.installmentsTotal) * 100;
  const daysUntilNextDebit = installment.nextDebitDate
    ? differenceInDays(parseISO(installment.nextDebitDate), new Date())
    : null;

  return (
    <div className="rounded-xl border border-danger/20 bg-danger/[0.03] p-4 hover:bg-danger/[0.06] transition">
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1 min-w-0">
          <p className="text-white font-semibold text-sm truncate">
            {installment.merchantLabel}
          </p>
          <p className="text-text-muted text-xs">{installment.provider}</p>
        </div>
        <div className="text-right shrink-0 ml-2">
          <p className="text-danger font-numeric font-bold">
            {fmt(installment.remainingAmount)}
          </p>
          <p className="text-xs text-text-muted">Reste</p>
        </div>
      </div>

      <div className="space-y-2">
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-text-muted">
              {installment.installmentsPaid} / {installment.installmentsTotal}{" "}
              versements
            </span>
            <span className="text-xs text-text-muted">
              {Math.round(progress)}%
            </span>
          </div>
          <div className="w-full h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
            <div
              className="h-full bg-danger transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs">
          <CreditCard size={12} className="text-danger shrink-0" />
          <span className="text-danger font-semibold">
            {fmt(installment.installmentAmount)} / mois
          </span>
          {installment.nextDebitDate && (
            <>
              <span className="text-text-muted">·</span>
              <span className="text-text-muted">
                Prochain :{" "}
                {format(parseISO(installment.nextDebitDate), "d MMM", {
                  locale: fr,
                })}
                {daysUntilNextDebit !== null && (
                  <span className="ml-1 font-semibold">
                    (dans {Math.max(0, daysUntilNextDebit)}j)
                  </span>
                )}
              </span>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

const LoanCard: React.FC<{ loan: LoanAmortization }> = ({ loan }) => {
  const payoffProgress =
    loan.paymentsMade && loan.estimatedPayoffDate
      ? Math.min(100, (loan.paymentsMade / 240) * 100)
      : 0;
  const daysUntilPayoff = loan.estimatedPayoffDate
    ? differenceInDays(parseISO(loan.estimatedPayoffDate), new Date())
    : null;

  return (
    <div className="rounded-xl border border-warning/20 bg-warning/[0.03] p-4 hover:bg-warning/[0.06] transition">
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1 min-w-0">
          <p className="text-white font-semibold text-sm truncate">
            {loan.lenderLabel}
          </p>
          <p className="text-text-muted text-xs">
            Crédit immobilier / à la consommation
          </p>
        </div>
        <div className="text-right shrink-0 ml-2">
          <p className="text-warning font-numeric font-bold">
            {fmt(loan.monthlyPayment)}
          </p>
          <p className="text-xs text-text-muted">/mois</p>
        </div>
      </div>

      <div className="space-y-2">
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-text-muted">
              {loan.paymentsMade || 0} versements effectués
            </span>
            <span className="text-xs text-text-muted">
              {Math.round(payoffProgress)}%
            </span>
          </div>
          <div className="w-full h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
            <div
              className="h-full bg-warning transition-all duration-300"
              style={{ width: `${payoffProgress}%` }}
            />
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs">
          {loan.estimatedPayoffDate ? (
            <>
              <Calendar size={12} className="text-warning shrink-0" />
              <span className="text-text-muted">
                Remboursement prévu :{" "}
                {format(parseISO(loan.estimatedPayoffDate), "MMMM yyyy", {
                  locale: fr,
                })}
              </span>
              {daysUntilPayoff !== null && (
                <span className="font-semibold text-warning ml-1">
                  ({loan.yearsUntilPayoff} ans)
                </span>
              )}
            </>
          ) : (
            <span className="text-text-muted">
              Informations de remboursement indisponibles
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

const DebtPage: React.FC = () => {
  const qc = useQueryClient();

  const { data: bnplData, isLoading: bnplLoading } = useQuery({
    queryKey: ["bnpl-list"],
    queryFn: bnplApi.list,
    retry: false,
  });

  const { data: loansData, isLoading: loansLoading } = useQuery({
    queryKey: ["loans-list"],
    queryFn: loansApi.list,
    retry: false,
  });

  const detectBnpl = useMutation({
    mutationFn: bnplApi.detect,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["bnpl-list"] }),
  });

  const detectLoans = useMutation({
    mutationFn: loansApi.detect,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["loans-list"] }),
  });

  const bnpl = bnplData?.installments || [];
  const loans = loansData?.loans || [];
  const totalBnplDebt = bnplData?.totalRemainingDebt || 0;
  const totalLoanBurden = loansData?.totalMonthlyBurden || 0;
  const totalMonthlyDebtPayment =
    (bnpl?.reduce((s, b) => s + b.installmentAmount, 0) || 0) + totalLoanBurden;

  return (
    <Layout title="Dettes et emprunts">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="page-title">Dettes et emprunts</h1>
            <p className="page-subtitle">
              BNPL (Achats à tempérament) · Crédits immobiliers · Prêts
              personnels
            </p>
          </div>
        </div>

        {/* Overview KPIs */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              BNPL en cours{" "}
              <HelpTooltip text="Montant total restant à payer pour tous les achats à tempérament (Oney, Klarna, Alma, etc.)" />
            </p>
            <p className="text-2xl font-bold font-numeric text-danger">
              {fmt(totalBnplDebt)}
            </p>
            <p className="text-xs text-text-muted mt-1">
              {bnpl.length} BNPL détectés
            </p>
          </Card>

          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Crédits en cours{" "}
              <HelpTooltip text="Charge mensuelle totale pour les prêts immobiliers et crédits à la consommation" />
            </p>
            <p className="text-2xl font-bold font-numeric text-warning">
              {fmt(totalLoanBurden)}
            </p>
            <p className="text-xs text-text-muted mt-1">
              {loans.length} crédits détectés
            </p>
          </Card>

          <Card padding="sm">
            <p className="text-text-secondary text-xs uppercase tracking-wider mb-2 flex items-center gap-1">
              Charge mensuelle totale{" "}
              <HelpTooltip text="Somme de tous les versements mensuels (BNPL + crédits)" />
            </p>
            <p className="text-2xl font-bold font-numeric text-white">
              {fmt(totalMonthlyDebtPayment)}
            </p>
            <p className="text-xs text-text-muted mt-1">
              À prévoir mensuellement
            </p>
          </Card>
        </div>

        {/* BNPL Section */}
        {(bnplLoading || bnpl.length > 0) && (
          <Card>
            <CardHeader
              title="Achats à tempérament (BNPL)"
              icon={<CreditCard size={16} />}
              action={
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={detectBnpl.isPending}
                  onClick={() => detectBnpl.mutate()}
                  className="gap-1"
                >
                  <RefreshCw
                    size={14}
                    className={detectBnpl.isPending ? "animate-spin" : ""}
                  />
                  Actualiser
                </Button>
              }
            />
            {bnplLoading ? (
              <Loader text="Chargement…" />
            ) : bnpl.length === 0 ? (
              <div className="py-8 text-center text-text-muted">
                <CreditCard className="mx-auto mb-2 opacity-30" size={32} />
                <p className="text-sm">Aucun achat à tempérament détecté</p>
                <p className="text-xs mt-1">
                  Nous n'avons pas identifié de BNPL dans vos transactions
                </p>
              </div>
            ) : (
              <div className="mt-4 grid gap-3">
                {bnpl.map((b) => (
                  <BnplCard key={b.id} installment={b} />
                ))}
              </div>
            )}
          </Card>
        )}

        {/* Loans Section */}
        {(loansLoading || loans.length > 0) && (
          <Card>
            <CardHeader
              title="Crédits et emprunts"
              icon={<TrendingDown size={16} />}
              action={
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={detectLoans.isPending}
                  onClick={() => detectLoans.mutate()}
                  className="gap-1"
                >
                  <RefreshCw
                    size={14}
                    className={detectLoans.isPending ? "animate-spin" : ""}
                  />
                  Actualiser
                </Button>
              }
            />
            {loansLoading ? (
              <Loader text="Chargement…" />
            ) : loans.length === 0 ? (
              <div className="py-8 text-center text-text-muted">
                <TrendingDown className="mx-auto mb-2 opacity-30" size={32} />
                <p className="text-sm">Aucun crédit détecté</p>
                <p className="text-xs mt-1">
                  Nous n'avons pas identifié de crédits dans vos transactions
                </p>
              </div>
            ) : (
              <div className="mt-4 grid gap-3">
                {loans.map((l) => (
                  <LoanCard key={l.id} loan={l} />
                ))}
              </div>
            )}
          </Card>
        )}

        {/* No debts state */}
        {!bnplLoading &&
          !loansLoading &&
          bnpl.length === 0 &&
          loans.length === 0 && (
            <Card
              padding="lg"
              className="text-center border-success/20 bg-success/5"
            >
              <CheckCircle className="mx-auto mb-3 text-success" size={40} />
              <p className="text-white font-semibold">Aucune dette détectée</p>
              <p className="text-text-muted text-sm mt-2">
                Excellente nouvelle ! Nous n'avons identifié aucun BNPL ou
                crédit dans vos transactions.
              </p>
            </Card>
          )}
      </div>
    </Layout>
  );
};

export default DebtPage;
