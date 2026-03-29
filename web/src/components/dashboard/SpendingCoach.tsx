import React from "react";
import {
  AlertTriangle,
  CheckCircle,
  CreditCard,
  TrendingUp,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/Card";
import { spendingPatternApi } from "@/api/spendingPattern";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

/**
 * SpendingCoach — "votre femme digitale"
 * Watches your spending in real-time and nags when you overspend.
 * Compares today vs 90-day daily average, weekend vs historic weekend,
 * and surfaces hidden subscriptions you forgot about.
 */
export const SpendingCoach: React.FC = () => {
  const { data, isLoading } = useQuery({
    queryKey: ["spending-patterns"],
    queryFn: spendingPatternApi.get,
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading || !data) return null;

  const isAnomaly = data.todayIsAnomaly || data.weekendIsAnomaly;
  const todayPct = Math.round(data.todayVsAvgRatio * 100);
  // Progress bar: 100% width = 2× average (bar saturates at 200%)
  const barWidth = Math.min(100, (data.todayVsAvgRatio / 2) * 100);
  const hasHidden = data.hiddenSubscriptions.length > 0;

  const coachMessage = (): string => {
    if (data.todayVsAvgRatio >= 3)
      return `Vous dépensez ${todayPct}% de votre moyenne aujourd'hui — sérieusement, vérifiez vos achats.`;
    if (data.todayIsAnomaly)
      return `${todayPct}% de votre habitude quotidienne aujourd'hui. Vous partez un peu vite.`;
    if (data.weekendIsAnomaly)
      return "Ce weekend coûte cher. Les petites dépenses s'accumulent vite.";
    if (todayPct < 30 && data.todayTotal > 0)
      return "Bonne journée — vous dépensez peu.";
    if (data.todayTotal === 0) return "Aucune dépense enregistrée aujourd'hui.";
    return "Dans la norme. Continuez comme ça.";
  };

  return (
    <Card padding="md">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div
            className={`p-1.5 rounded-lg ${isAnomaly ? "bg-warning/15" : "bg-success/15"}`}
          >
            {isAnomaly ? (
              <AlertTriangle size={15} className="text-warning" />
            ) : (
              <CheckCircle size={15} className="text-success" />
            )}
          </div>
          <h3
            className="text-white font-semibold text-sm"
            style={{ fontFamily: "var(--font-display)" }}
          >
            Sentinelle dépenses
          </h3>
        </div>
        <Link
          to="/spending"
          className="text-xs text-text-muted hover:text-primary transition"
        >
          Détail →
        </Link>
      </div>

      <div className="space-y-4">
        {/* Today's spending vs average */}
        <div>
          <div className="flex items-center justify-between text-xs mb-2">
            <span className="text-text-muted">Aujourd&apos;hui</span>
            <div className="flex items-center gap-2">
              <span className="text-text-muted">
                moy. {fmt(data.dailyAverage)}/j
              </span>
              <span
                className={`font-numeric font-bold ${
                  data.todayIsAnomaly ? "text-warning" : "text-white"
                }`}
              >
                {fmt(data.todayTotal)}
              </span>
            </div>
          </div>
          {/* Progress bar: green → warning as spending rises */}
          <div className="w-full h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                data.todayIsAnomaly ? "bg-warning" : "bg-success"
              }`}
              style={{ width: `${barWidth}%` }}
            />
          </div>
          <p
            className={`text-xs mt-2 leading-relaxed ${
              isAnomaly ? "text-warning" : "text-text-muted"
            }`}
          >
            {coachMessage()}
          </p>
        </div>

        {/* Weekend anomaly banner */}
        {data.weekendIsAnomaly && (
          <div className="flex items-start gap-2.5 p-3 rounded-lg bg-warning/[0.08] border border-warning/20">
            <TrendingUp size={14} className="text-warning mt-0.5 shrink-0" />
            <div>
              <p className="text-warning text-xs font-semibold mb-0.5">
                Weekend dépensier
              </p>
              <p className="text-text-secondary text-xs leading-relaxed">
                +
                {Math.round((data.weekendVsWeekdayRatio - 1) * 100)}% vs vos
                weekends habituels. Les sorties, restos et livraisons
                s'accumulent.
              </p>
            </div>
          </div>
        )}

        {/* Weekday vs weekend comparison — shown when no anomaly */}
        {!data.weekendIsAnomaly &&
          data.weekdayDailyAverage > 0 &&
          data.weekendDailyAverage > 0 && (
            <div className="grid grid-cols-2 gap-2">
              <div className="text-center p-2.5 rounded-lg bg-white/[0.03] border border-white/[0.04]">
                <p className="text-text-muted text-xs mb-1">Semaine</p>
                <p className="text-white font-numeric text-sm font-semibold">
                  {fmt(data.weekdayDailyAverage)}
                  <span className="text-text-muted text-xs font-normal">
                    /j
                  </span>
                </p>
              </div>
              <div className="text-center p-2.5 rounded-lg bg-white/[0.03] border border-white/[0.04]">
                <p className="text-text-muted text-xs mb-1">Weekend</p>
                <p
                  className={`font-numeric text-sm font-semibold ${
                    data.weekendVsWeekdayRatio > 1.3
                      ? "text-warning"
                      : "text-white"
                  }`}
                >
                  {fmt(data.weekendDailyAverage)}
                  <span className="text-text-muted text-xs font-normal">
                    /j
                  </span>
                </p>
              </div>
            </div>
          )}

        {/* Hidden subscriptions */}
        {hasHidden && (
          <div>
            <p className="text-xs text-text-muted uppercase tracking-widest font-medium mb-2">
              Abonnements détectés non déclarés
            </p>
            <div className="space-y-1.5">
              {data.hiddenSubscriptions.slice(0, 3).map((sub, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between px-3 py-2 rounded-lg bg-white/[0.03] border border-white/[0.04]"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <CreditCard size={12} className="text-primary shrink-0" />
                    <span className="text-text-secondary text-xs truncate capitalize">
                      {sub.label}
                    </span>
                    <span className="text-text-muted text-xs shrink-0">
                      ×{sub.monthsDetected} mois
                    </span>
                  </div>
                  <span className="text-white font-numeric text-xs font-semibold ml-2 shrink-0">
                    {fmt(sub.monthlyAmount)}
                    <span className="text-text-muted font-normal">/mois</span>
                  </span>
                </div>
              ))}
            </div>
            <p className="text-text-muted text-xs mt-2">
              Ces charges récurrentes ne sont pas catégorisées comme
              abonnements.{" "}
              <Link
                to="/transactions"
                className="text-primary hover:underline"
              >
                Les vérifier
              </Link>
            </p>
          </div>
        )}
      </div>
    </Card>
  );
};
