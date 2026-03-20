import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  TrendingDown,
  Clock,
  Zap,
  RefreshCw,
  CheckCircle,
  XCircle,
  ChevronRight,
  Shield,
  BarChart3,
  FileText,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import {
  decisionEngineApi,
  type CashDriver,
  type CashAction,
  type ScenarioType,
  type RiskLevel,
} from "@/api/decisionEngine";

// ── Risk badge ──────────────────────────────────────────────────────────────

const RISK_CONFIG: Record<
  RiskLevel,
  {
    label: string;
    bg: string;
    text: string;
    border: string;
    icon: React.ReactNode;
  }
> = {
  LOW: {
    label: "Faible",
    bg: "bg-success/10",
    text: "text-success",
    border: "border-success/30",
    icon: <Shield className="w-4 h-4" />,
  },
  MEDIUM: {
    label: "Modéré",
    bg: "bg-warning/10",
    text: "text-warning",
    border: "border-warning/30",
    icon: <BarChart3 className="w-4 h-4" />,
  },
  HIGH: {
    label: "Élevé",
    bg: "bg-orange-900/20",
    text: "text-orange-400",
    border: "border-orange-500/30",
    icon: <TrendingDown className="w-4 h-4" />,
  },
  CRITICAL: {
    label: "Critique",
    bg: "bg-danger/10",
    text: "text-danger",
    border: "border-danger/30",
    icon: <AlertTriangle className="w-4 h-4" />,
  },
};

const ACTION_TYPE_LABELS: Record<string, string> = {
  DELAY_SUPPLIER: "Reporter fournisseur",
  SEND_REMINDERS: "Relancer client",
  REDUCE_SPEND: "Réduire dépenses",
  REQUEST_CREDIT: "Demander crédit",
  ACCELERATE_RECEIVABLES: "Accélérer encaissements",
};

const DRIVER_TYPE_ICONS: Record<string, React.ReactNode> = {
  TAX_PAYMENT: <FileText className="w-4 h-4 text-purple-500" />,
  LATE_INVOICE: <Clock className="w-4 h-4 text-orange-500" />,
  RECURRING_COST: <TrendingDown className="w-4 h-4 text-red-500" />,
  SUPPLIER_PAYMENT: <TrendingDown className="w-4 h-4 text-blue-500" />,
  REVENUE_DROP: <TrendingDown className="w-4 h-4 text-red-600" />,
};

// ── Scenario simulation panel ────────────────────────────────────────────────

const SCENARIOS: { type: ScenarioType; label: string; description: string }[] =
  [
    {
      type: "HIRE_EMPLOYEE",
      label: "Embaucher un salarié",
      description: "Impact d'une nouvelle embauche sur 12 mois",
    },
    {
      type: "REVENUE_DROP",
      label: "Baisse de CA",
      description: "Simuler une baisse de chiffre d'affaires",
    },
    {
      type: "PAYMENT_DELAY",
      label: "Reporter un paiement",
      description: "Décaler un paiement fournisseur",
    },
  ];

function ScenarioPanel({
  currentBalance,
}: Readonly<{ currentBalance: number }>) {
  const [selected, setSelected] = useState<ScenarioType | null>(null);
  const [amount, setAmount] = useState("");
  const [percentage, setPercentage] = useState("20");
  const [daysDelay, setDaysDelay] = useState("30");

  const simulateMutation = useMutation({
    mutationFn: decisionEngineApi.simulate,
  });

  const handleSimulate = () => {
    if (!selected) return;
    simulateMutation.mutate({
      scenarioType: selected,
      amount: amount ? Number.parseFloat(amount) : undefined,
      percentage:
        selected === "REVENUE_DROP" ? Number.parseFloat(percentage) : undefined,
      daysDelay:
        selected === "PAYMENT_DELAY"
          ? Number.parseInt(daysDelay, 10)
          : undefined,
    });
  };

  return (
    <Card className="p-5">
      <h3 className="font-semibold text-white mb-4 flex items-center gap-2">
        <Zap className="w-4 h-4 text-primary" />
        Simulateur de scénarios
        <HelpTooltip text="Testez l'impact financier de vos décisions (embauche, baisse de CA, report de paiement…) sur votre trésorerie avant de vous engager." />
      </h3>
      <div className="space-y-2 mb-4">
        {SCENARIOS.map((s) => (
          <button
            key={s.type}
            onClick={() => {
              setSelected(s.type);
              simulateMutation.reset();
            }}
            className={`w-full text-left p-3 rounded-lg border transition-all ${
              selected === s.type
                ? "border-primary bg-primary/10"
                : "border-white/[0.08] hover:border-white/[0.16] bg-white/[0.02]"
            }`}
          >
            <div className="font-medium text-sm text-white">{s.label}</div>
            <div className="text-xs text-text-secondary">{s.description}</div>
          </button>
        ))}
      </div>

      {selected === "HIRE_EMPLOYEE" && (
        <input
          type="number"
          className="w-full border border-white/[0.12] rounded-lg px-3 py-2 text-sm mb-3 bg-white/[0.04] text-white placeholder:text-text-muted focus:outline-none focus:border-primary"
          placeholder="Salaire mensuel (€, défaut 3 500€)"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
      )}
      {selected === "REVENUE_DROP" && (
        <div className="mb-3">
          <label className="text-xs text-text-secondary block mb-1">
            Baisse de CA : {percentage}%
          </label>
          <input
            type="range"
            min="5"
            max="80"
            value={percentage}
            onChange={(e) => setPercentage(e.target.value)}
            className="w-full accent-primary"
          />
        </div>
      )}
      {selected === "PAYMENT_DELAY" && (
        <div className="flex gap-2 mb-3">
          <input
            type="number"
            className="flex-1 border border-white/[0.12] rounded-lg px-3 py-2 text-sm bg-white/[0.04] text-white placeholder:text-text-muted focus:outline-none focus:border-primary"
            placeholder="Montant (€)"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <input
            type="number"
            className="w-24 border border-white/[0.12] rounded-lg px-3 py-2 text-sm bg-white/[0.04] text-white placeholder:text-text-muted focus:outline-none focus:border-primary"
            placeholder="Jours"
            value={daysDelay}
            onChange={(e) => setDaysDelay(e.target.value)}
          />
        </div>
      )}

      <Button
        onClick={handleSimulate}
        disabled={!selected || simulateMutation.isPending}
        className="w-full"
      >
        {simulateMutation.isPending ? "Calcul…" : "Simuler"}
      </Button>

      {simulateMutation.data && (
        <div className="mt-4 p-4 bg-white/[0.04] border border-white/[0.08] rounded-xl space-y-2">
          <p className="text-sm font-medium text-white">Résultat</p>
          <p className="text-xs text-text-secondary">
            {simulateMutation.data.explanation}
          </p>
          <div className="grid grid-cols-2 gap-3 mt-2">
            <div className="text-center">
              <div className="text-xs text-text-secondary">
                Trésorerie actuelle
              </div>
              <div className="font-semibold text-white">
                {(simulateMutation.data.baseBalance ?? 0).toLocaleString(
                  "fr-FR",
                  {
                    style: "currency",
                    currency: "EUR",
                  },
                )}
              </div>
            </div>
            <div className="text-center">
              <div className="text-xs text-text-secondary">Après scénario</div>
              <div
                className={`font-semibold ${
                  (simulateMutation.data.projectedBalance ?? 0) <
                  (simulateMutation.data.baseBalance ?? 0)
                    ? "text-danger"
                    : "text-success"
                }`}
              >
                {(simulateMutation.data.projectedBalance ?? 0).toLocaleString(
                  "fr-FR",
                  { style: "currency", currency: "EUR" },
                )}
              </div>
            </div>
          </div>
          <div className="flex justify-between text-xs text-text-secondary pt-1 border-t border-white/[0.08]">
            <span>Runway actuel : {simulateMutation.data.baseRunwayDays}j</span>
            <span>
              Après :{" "}
              <span
                className={
                  simulateMutation.data.projectedRunwayDays <
                  simulateMutation.data.baseRunwayDays
                    ? "text-danger font-medium"
                    : "text-success font-medium"
                }
              >
                {simulateMutation.data.projectedRunwayDays}j
              </span>
            </span>
          </div>
        </div>
      )}
    </Card>
  );
}

// ── Weekly Brief panel ────────────────────────────────────────────────────────

function WeeklyBriefPanel() {
  const qc = useQueryClient();
  const { data: brief, isLoading } = useQuery({
    queryKey: ["weekly-brief"],
    queryFn: decisionEngineApi.getBrief,
    retry: false,
  });

  const generateMutation = useMutation({
    mutationFn: decisionEngineApi.generateBrief,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["weekly-brief"] }),
  });

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-white flex items-center gap-2">
          <FileText className="w-4 h-4 text-emerald-400" />
          Bulletin financier
          <HelpTooltip text="Synthèse de votre santé financière générée par le moteur de décision. Obtenez un bilan clair avec les actions prioritaires à mener." />
        </h3>
        <Button
          size="sm"
          variant="outline"
          onClick={() => generateMutation.mutate()}
          disabled={generateMutation.isPending}
        >
          <RefreshCw
            className={`w-3.5 h-3.5 mr-1 ${generateMutation.isPending ? "animate-spin" : ""}`}
          />
          Générer
        </Button>
      </div>
      {isLoading && (
        <div className="h-24 bg-white/[0.06] rounded animate-pulse" />
      )}
      {!isLoading && brief && (
        <div>
          <p className="text-sm text-text-secondary leading-relaxed whitespace-pre-line">
            {brief.briefText}
          </p>
          <p className="text-xs text-text-muted mt-3">
            Généré le{" "}
            {new Date(brief.generatedAt).toLocaleDateString("fr-FR", {
              day: "numeric",
              month: "long",
              year: "numeric",
            })}
          </p>
        </div>
      )}
      {!isLoading && !brief && (
        <div className="text-center py-6 text-sm text-text-secondary">
          <p>Aucun bulletin disponible.</p>
          <Button
            size="sm"
            className="mt-3"
            onClick={() => generateMutation.mutate()}
            disabled={generateMutation.isPending}
          >
            Générer maintenant
          </Button>
        </div>
      )}
    </Card>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

const FinancialControlCenterPage: React.FC = () => {
  const qc = useQueryClient();

  const {
    data: summary,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["decision-engine-summary"],
    queryFn: decisionEngineApi.getSummary,
    staleTime: 5 * 60_000, // 5 min
  });

  const refreshMutation = useMutation({
    mutationFn: decisionEngineApi.refresh,
    onSuccess: (data) => {
      qc.setQueryData(["decision-engine-summary"], data);
    },
  });

  const applyMutation = useMutation({
    mutationFn: decisionEngineApi.applyAction,
    onSuccess: () => refetch(),
  });

  const dismissMutation = useMutation({
    mutationFn: decisionEngineApi.dismissAction,
    onSuccess: () => refetch(),
  });

  if (isLoading) {
    return (
      <Layout>
        <div className="max-w-6xl mx-auto space-y-4 animate-pulse">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-32 bg-white/[0.06] rounded-xl" />
          ))}
        </div>
      </Layout>
    );
  }

  if (isError || !summary) {
    return (
      <Layout>
        <div className="max-w-6xl mx-auto text-center py-16">
          <AlertTriangle className="w-10 h-10 text-red-400 mx-auto mb-4" />
          <p className="text-gray-600 mb-4">
            Impossible de charger le moteur de décision.
          </p>
          <Button onClick={() => refetch()}>Réessayer</Button>
        </div>
      </Layout>
    );
  }

  const risk = RISK_CONFIG[summary.riskLevel] ?? RISK_CONFIG.LOW;

  return (
    <Layout>
      <div className="max-w-6xl mx-auto space-y-5 animate-slide-up">
        {/* ── Header ── */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-white">
              Centre de contrôle financier
            </h1>
            <p className="text-sm text-text-secondary">
              Mis à jour{" "}
              {new Date(summary.computedAt).toLocaleTimeString("fr-FR", {
                hour: "2-digit",
                minute: "2-digit",
              })}
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refreshMutation.mutate()}
            disabled={refreshMutation.isPending}
          >
            <RefreshCw
              className={`w-4 h-4 mr-2 ${refreshMutation.isPending ? "animate-spin" : ""}`}
            />
            Actualiser
          </Button>
        </div>

        {/* ── Top KPI strip ── */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {/* Risk level */}
          <Card className={`p-4 border ${risk.border} ${risk.bg}`}>
            <div
              className={`flex items-center gap-2 ${risk.text} font-semibold text-sm mb-1`}
            >
              {risk.icon}
              Risque
              <HelpTooltip text="Niveau de risque global calculé à partir de votre runway, de vos échéances à venir et de la volatilité de votre trésorerie." />
            </div>
            <div className={`text-2xl font-bold ${risk.text}`}>
              {risk.label}
            </div>
          </Card>

          {/* Runway */}
          <Card className="p-4">
            <div className="text-xs text-text-secondary mb-1 flex items-center gap-1">
              <Clock className="w-3.5 h-3.5" /> Runway
              <HelpTooltip text="Nombre de jours pendant lesquels votre trésorerie peut couvrir vos dépenses au rythme actuel, sans nouvel encaissement." />
            </div>
            <div className="text-2xl font-bold text-white">
              {summary.runwayDays < 180 ? `${summary.runwayDays}j` : "180j+"}
            </div>
            <div className="text-xs text-text-muted">trésorerie disponible</div>
          </Card>

          {/* Current balance */}
          <Card className="p-4">
            <div className="text-xs text-text-secondary mb-1 flex items-center gap-1">
              Solde actuel
              <HelpTooltip text="Somme des soldes de tous vos comptes bancaires actifs, synchronisés en temps réel via open banking." />
            </div>
            <div className="text-2xl font-bold text-white">
              {(summary.currentBalance ?? 0).toLocaleString("fr-FR", {
                style: "currency",
                currency: "EUR",
                maximumFractionDigits: 0,
              })}
            </div>
            <div className="text-xs text-text-muted">tous comptes actifs</div>
          </Card>

          {/* Min projected */}
          <Card className="p-4">
            <div className="text-xs text-text-secondary mb-1 flex items-center gap-1">
              Minimum projeté
              <HelpTooltip text="Solde le plus bas prévu dans les 30 prochains jours, tenant compte de vos dépenses récurrentes et charges connues." />
            </div>
            <div
              className={`text-2xl font-bold ${
                (summary.minProjectedBalance ?? 0) < 0
                  ? "text-danger"
                  : "text-white"
              }`}
            >
              {(summary.minProjectedBalance ?? 0).toLocaleString("fr-FR", {
                style: "currency",
                currency: "EUR",
                maximumFractionDigits: 0,
              })}
            </div>
            {summary.minProjectedDate && (
              <div className="text-xs text-text-muted">
                le{" "}
                {new Date(summary.minProjectedDate).toLocaleDateString("fr-FR")}
              </div>
            )}
          </Card>
        </div>

        <div className="grid lg:grid-cols-3 gap-5">
          {/* Left: drivers + actions */}
          <div className="lg:col-span-2 space-y-5">
            {/* ── Cash Drivers ── */}
            <Card className="p-5">
              <h3 className="font-semibold text-white mb-4 flex items-center gap-2">
                <TrendingDown className="w-4 h-4 text-danger" />
                Facteurs impactant votre trésorerie
                <HelpTooltip text="Événements détectés qui pèsent sur votre trésorerie : factures impayées, échéances fiscales, charges récurrentes…" />
              </h3>
              {(summary.drivers?.length ?? 0) === 0 ? (
                <p className="text-sm text-text-secondary">
                  Aucun facteur détecté — situation saine.
                </p>
              ) : (
                <ul className="space-y-3">
                  {(summary.drivers ?? []).map((d: CashDriver) => (
                    <li
                      key={d.id}
                      className="flex items-start gap-3 p-3 bg-white/[0.04] rounded-lg"
                    >
                      <span className="mt-0.5">
                        {DRIVER_TYPE_ICONS[d.type] ?? (
                          <ChevronRight className="w-4 h-4" />
                        )}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-white">{d.label}</p>
                        {d.dueDate && (
                          <p className="text-xs text-text-secondary">
                            Échéance :{" "}
                            {new Date(d.dueDate).toLocaleDateString("fr-FR")}
                          </p>
                        )}
                      </div>
                      {(d.amount ?? 0) > 0 && (
                        <span className="text-sm font-semibold text-danger whitespace-nowrap">
                          -
                          {(d.amount ?? 0).toLocaleString("fr-FR", {
                            style: "currency",
                            currency: "EUR",
                            maximumFractionDigits: 0,
                          })}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            {/* ── Recommended Actions ── */}
            <Card className="p-5">
              <h3 className="font-semibold text-white mb-4 flex items-center gap-2">
                <Zap className="w-4 h-4 text-primary" />
                Actions recommandées
                <HelpTooltip text="Suggestions concrètes générées par le moteur de décision pour améliorer votre situation. Marquez-les comme appliquées ou ignorez-les." />
              </h3>
              {(summary.actions?.length ?? 0) === 0 ? (
                <p className="text-sm text-gray-500">
                  Aucune action nécessaire pour le moment.
                </p>
              ) : (
                <ul className="space-y-3">
                  {(summary.actions ?? [])
                    .filter((a: CashAction) => a.status === "PENDING")
                    .map((action: CashAction) => (
                      <li
                        key={action.id}
                        className="p-4 border border-white/[0.08] rounded-xl hover:border-primary/30 transition-colors"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex-1 min-w-0">
                            <span className="inline-block text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full mb-1.5">
                              {ACTION_TYPE_LABELS[action.actionType] ??
                                action.actionType}
                            </span>
                            <p className="text-sm text-text-secondary">
                              {action.description}
                            </p>
                          </div>
                          <div className="text-right shrink-0">
                            <div className="text-sm font-semibold text-success">
                              +
                              {(action.estimatedImpact ?? 0).toLocaleString(
                                "fr-FR",
                                {
                                  style: "currency",
                                  currency: "EUR",
                                  maximumFractionDigits: 0,
                                },
                              )}
                            </div>
                            <div className="text-xs text-text-muted">
                              sous {action.horizonDays}j
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center justify-between mt-3">
                          <div className="flex items-center gap-1.5">
                            <div className="h-1.5 w-20 bg-white/[0.10] rounded-full overflow-hidden">
                              <div
                                className="h-full bg-primary rounded-full"
                                style={{
                                  width: `${(action.confidence ?? 0) * 100}%`,
                                }}
                              />
                            </div>
                            <span className="text-xs text-text-secondary">
                              {Math.round((action.confidence ?? 0) * 100)}%
                              confiance
                            </span>
                          </div>
                          <div className="flex gap-2">
                            <button
                              onClick={() => applyMutation.mutate(action.id)}
                              disabled={applyMutation.isPending}
                              className="flex items-center gap-1 text-xs text-success hover:text-success font-medium"
                              title="Marquer comme appliqué"
                            >
                              <CheckCircle className="w-3.5 h-3.5" />
                              Appliqué
                            </button>
                            <button
                              onClick={() => dismissMutation.mutate(action.id)}
                              disabled={dismissMutation.isPending}
                              className="flex items-center gap-1 text-xs text-text-muted hover:text-text-secondary"
                              title="Ignorer"
                            >
                              <XCircle className="w-3.5 h-3.5" />
                              Ignorer
                            </button>
                          </div>
                        </div>
                      </li>
                    ))}
                </ul>
              )}
            </Card>
          </div>

          {/* Right: scenario + brief */}
          <div className="space-y-5">
            <ScenarioPanel currentBalance={summary.currentBalance} />
            <WeeklyBriefPanel />
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default FinancialControlCenterPage;
