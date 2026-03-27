import React, { useState } from "react";
import { TrendingUp, AlertTriangle, Calendar, Zap, Lock } from "lucide-react";
import { Link } from "react-router-dom";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader, CardSkeleton } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { ForecastChart } from "@/components/charts/ForecastChart";
import { ConfidenceBadge } from "@/components/ui/ConfidenceBadge";
import { useForecast } from "@/hooks/useForecast";
import { useAuthStore } from "@/store/authStore";
import { format, parseISO, differenceInDays } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

const toConfidenceLevel = (score: number) => {
  if (score >= 0.85) return "HIGH" as const;
  if (score >= 0.65) return "MEDIUM" as const;
  if (score >= 0.45) return "LOW" as const;
  return "INSUFFICIENT" as const;
};

const ForecastPage: React.FC = () => {
  const [horizon, setHorizon] = useState(30);
  const [showUpsell, setShowUpsell] = useState(false);
  const { data: forecast, isLoading } = useForecast(horizon);
  const { user } = useAuthStore();
  const isPro = user?.role === "ROLE_BUSINESS";

  const handleHorizonChange = (h: number) => {
    if (h > 30 && !isPro) {
      setShowUpsell(true);
      return;
    }
    setShowUpsell(false);
    setHorizon(h);
  };

  return (
    <Layout title="Prévisions">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="page-title">Prévisions de trésorerie</h1>
            <p className="page-subtitle">
              Modèle IA mis à jour quotidiennement à 06h00
            </p>
          </div>
          {forecast && (
            <ConfidenceBadge
              level={toConfidenceLevel(forecast.confidenceScore)}
              historyDays={90}
            />
          )}
        </div>

        {/* KPI grid */}
        {isLoading ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <CardSkeleton key={i} lines={2} />
            ))}
          </div>
        ) : (
          forecast && (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Health score */}
              <Card padding="sm">
                <p className="text-text-secondary text-xs uppercase tracking-wider font-medium mb-2 flex items-center gap-1">
                  Score santé{" "}
                  <HelpTooltip text="Score global de santé financière de 0 à 100, basé sur le solde, les tendances et les échéances à venir." />
                </p>
                <p
                  className={`text-2xl font-bold font-numeric ${
                    forecast.healthScore >= 70
                      ? "text-success"
                      : forecast.healthScore >= 40
                        ? "text-warning"
                        : "text-danger"
                  }`}
                >
                  {forecast.healthScore}
                  <span className="text-sm text-text-muted">/100</span>
                </p>
              </Card>
              {/* Confidence */}
              <Card padding="sm">
                <p className="text-text-secondary text-xs uppercase tracking-wider font-medium mb-2 flex items-center gap-1">
                  Fiabilité IA{" "}
                  <HelpTooltip text="Pourcentage de confiance du modèle de prévision, recalculé quotidiennement à partir de vos transactions réelles." />
                </p>
                <p
                  className={`text-2xl font-bold font-numeric ${
                    forecast.confidenceScore >= 0.75
                      ? "text-success"
                      : forecast.confidenceScore >= 0.5
                        ? "text-warning"
                        : "text-danger"
                  }`}
                >
                  {Math.round(forecast.confidenceScore * 100)}
                  <span className="text-sm text-text-muted">%</span>
                </p>
              </Card>
              {/* Critical points */}
              <Card padding="sm">
                <p className="text-text-secondary text-xs uppercase tracking-wider font-medium mb-2 flex items-center gap-1">
                  Vigilances{" "}
                  <HelpTooltip text="Nombre de dates où votre solde prévu pourrait être insuffisant ou négatif dans la période analysée." />
                </p>
                <p
                  className={`text-2xl font-bold font-numeric ${
                    forecast.criticalPoints.length > 0
                      ? "text-danger"
                      : "text-success"
                  }`}
                >
                  {forecast.criticalPoints.length}
                </p>
              </Card>
              {/* Last update */}
              <Card padding="sm">
                <p className="text-text-secondary text-xs uppercase tracking-wider font-medium mb-2">
                  Mis à jour
                </p>
                <p className="text-sm font-semibold text-white">
                  {format(parseISO(forecast.generatedAt), "d MMM HH:mm", {
                    locale: fr,
                  })}
                </p>
              </Card>
            </div>
          )
        )}

        {/* FlashCredit deficit banner */}
        {forecast &&
          forecast.criticalPoints.some((cp) => cp.predictedBalance < 0) && (
            <div className="flex items-center gap-3 px-4 py-3.5 bg-danger/[0.07] border border-danger/20 rounded-xl">
              <Zap size={16} className="text-danger flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-white">
                  Déficit de trésorerie prévu dans{" "}
                  {Math.max(
                    0,
                    differenceInDays(
                      parseISO(
                        forecast.criticalPoints.find(
                          (cp) => cp.predictedBalance < 0,
                        )!.date,
                      ),
                      new Date(),
                    ),
                  )}{" "}
                  jours
                </p>
                <p className="text-xs text-text-muted mt-0.5">
                  Activez la Réserve FlashCredit pour couvrir ce déficit sans
                  délai.
                </p>
              </div>
              <Link to="/flash-credit" className="flex-shrink-0">
                <Button
                  variant="gradient"
                  size="sm"
                  leftIcon={<Zap size={13} />}
                >
                  Activer la Réserve
                </Button>
              </Link>
            </div>
          )}

        {/* PRO upsell inline */}
        {showUpsell && (
          <div className="flex items-center gap-3 px-4 py-3 bg-primary/[0.06] border border-primary/15 rounded-xl text-sm text-text-secondary">
            <Lock size={14} className="text-primary flex-shrink-0" />
            <span>
              Les horizons 60j et 90j sont disponibles avec FlowGuard Pro.
            </span>
            <Link to="/subscription">
              <Button
                variant="gradient"
                size="xs"
                leftIcon={<Zap size={12} />}
                className="ml-auto"
              >
                Passer Pro
              </Button>
            </Link>
          </div>
        )}

        {/* Forecast chart */}
        <Card>
          <CardHeader
            title={`Évolution du solde — ${horizon} jours`}
            subtitle="Zone colorée = fourchette d'estimation · Ligne rouge = seuil d'alerte"
            icon={<TrendingUp size={18} />}
            helpTooltip={
              <HelpTooltip text="Projection de votre solde sur l'horizon choisi. La zone colorée représente la fourchette de confiance du modèle IA." />
            }
          />
          {isLoading ? (
            <Loader text="Calcul des prévisions…" />
          ) : forecast ? (
            <ForecastChart
              data={forecast}
              height={340}
              userRole={user?.role as "ROLE_USER" | "ROLE_BUSINESS" | undefined}
              horizon={horizon}
              onHorizonChange={handleHorizonChange}
              onUpgrade={() => setShowUpsell(true)}
            />
          ) : (
            <div className="py-12 text-center text-text-muted text-sm">
              Aucune donnée de prévision disponible
            </div>
          )}
        </Card>

        {/* Critical points */}
        {forecast && forecast.criticalPoints.length > 0 && (
          <Card>
            <CardHeader
              title="Points de vigilance"
              subtitle="Dates où votre solde pourrait être insuffisant"
              icon={<AlertTriangle size={18} />}
              helpTooltip={
                <HelpTooltip text="Dates critiques prévisionnelles où votre trésorerie pourrait passer sous zéro. Agissez à l'avance pour les éviter." />
              }
            />
            <div className="space-y-3 mt-2">
              {forecast.criticalPoints.map((cp, i) => {
                const daysUntil = differenceInDays(
                  parseISO(cp.date),
                  new Date(),
                );
                const isUrgent = daysUntil <= 7;
                return (
                  <div
                    key={i}
                    className={`flex items-start gap-4 p-4 border rounded-xl ${
                      isUrgent
                        ? "bg-danger/[0.05] border-danger/20"
                        : "bg-warning/[0.04] border-warning/15"
                    }`}
                  >
                    <Calendar
                      size={16}
                      className={`${isUrgent ? "text-danger" : "text-warning"} flex-shrink-0 mt-0.5`}
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        <p className="text-white font-medium text-sm">
                          {format(parseISO(cp.date), "d MMMM yyyy", {
                            locale: fr,
                          })}
                        </p>
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            isUrgent
                              ? "bg-danger/20 text-danger"
                              : "bg-warning/20 text-warning"
                          }`}
                        >
                          {daysUntil <= 0
                            ? "Aujourd'hui"
                            : `Dans ${daysUntil}j`}
                        </span>
                      </div>
                      <p className="text-text-secondary text-sm">{cp.reason}</p>
                      <p
                        className={`text-sm font-medium font-numeric mt-1.5 ${
                          isUrgent ? "text-danger" : "text-warning"
                        }`}
                      >
                        Solde prévu : {fmt(cp.predictedBalance)}
                      </p>
                    </div>
                    {isUrgent && (
                      <Link to="/flash-credit" className="flex-shrink-0">
                        <Button
                          variant="gradient"
                          size="sm"
                          leftIcon={<Zap size={13} />}
                        >
                          Réserve
                        </Button>
                      </Link>
                    )}
                  </div>
                );
              })}
            </div>
          </Card>
        )}
      </div>
    </Layout>
  );
};

export default ForecastPage;
