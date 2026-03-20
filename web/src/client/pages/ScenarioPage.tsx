import React, { useState, useEffect, useRef } from "react";
import { GitBranch, Info, Zap } from "lucide-react";
import { Link } from "react-router-dom";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { useScenario } from "@/hooks/useScenario";
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
  Legend,
} from "recharts";
import { format, addDays } from "date-fns";
import { fr } from "date-fns/locale";
import type { ScenarioResponse } from "@/domain/Scenario";

const SCENARIO_TYPES = [
  { value: "LATE_PAYMENT", label: "Retard de paiement client" },
  { value: "EXTRA_EXPENSE", label: "Dépense imprévue" },
  { value: "EARLY_INVOICE", label: "Facturation anticipée" },
  { value: "LOST_CLIENT", label: "Perte d'un client" },
  { value: "NEW_HIRE", label: "Nouvel employé" },
  { value: "INVESTMENT", label: "Investissement" },
];

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

const RISK_CONFIG: Record<
  string,
  { color: string; bg: string; label: string }
> = {
  LOW: { color: "text-success", bg: "bg-success/15", label: "Faible" },
  MEDIUM: { color: "text-warning", bg: "bg-warning/15", label: "Modéré" },
  HIGH: { color: "text-danger", bg: "bg-danger/15", label: "Élevé" },
  CRITICAL: { color: "text-danger", bg: "bg-danger/20", label: "Critique" },
};

const ScenarioPage: React.FC = () => {
  const { mutateAsync, isPending } = useScenario();

  const [type, setType] = useState(SCENARIO_TYPES[0].value);
  const [amount, setAmount] = useState(5000);
  const [delayDays, setDelayDays] = useState(30);
  const [desc, setDesc] = useState("");
  const [result, setResult] = useState<ScenarioResponse | null>(null);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Auto-simulate on every param change (debounced 200ms — no Calculer button)
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await mutateAsync({
          type,
          amount,
          delayDays,
          description: desc,
        });
        setResult(res);
      } catch {
        // silent — show stale result or empty state
      }
    }, 200);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [type, amount, delayDays]); // eslint-disable-line react-hooks/exhaustive-deps

  // Build chart data from parallel number arrays + synthetic dates from today
  const today = new Date();
  const chartData =
    result?.baselineForecast?.map((base, i) => ({
      date: addDays(today, i),
      base,
      scenario: result.impactedForecast?.[i] ?? null,
    })) ?? [];

  const riskCfg = result
    ? (RISK_CONFIG[result.riskLevel] ?? RISK_CONFIG.MEDIUM)
    : null;

  return (
    <Layout title="Simulation de scénarios">
      <div className="max-w-5xl mx-auto space-y-6 animate-slide-up">
        {/* Header */}
        <div>
          <h1 className="page-title">Simulation de scénarios</h1>
          <p className="page-subtitle">
            Anticipez l'impact financier d'événements futurs · Calcul en temps
            réel
          </p>
        </div>

        <div className="grid lg:grid-cols-[380px_1fr] gap-6 items-start">
          {/* ── Controls panel ─────────────────────────────────────────────── */}
          <Card>
            <CardHeader
              title="Configurer le scénario"
              icon={<GitBranch size={18} />}
              helpTooltip={
                <HelpTooltip text="Simulez l'impact financier d'un événement futur sans engagement — montant et délai paramétrables, calcul en temps réel." />
              }
            />
            <div className="mt-5 space-y-5">
              {/* Scenario type */}
              <div>
                <label
                  htmlFor="scenario-type"
                  className="block text-sm font-medium text-text-secondary mb-1.5"
                >
                  Type de scénario
                </label>
                <select
                  id="scenario-type"
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm focus:outline-none focus:border-primary/40 focus:ring-2 focus:ring-primary/15 transition"
                >
                  {SCENARIO_TYPES.map((t) => (
                    <option
                      key={t.value}
                      value={t.value}
                      className="bg-[#0D1B3E]"
                    >
                      {t.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Amount */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label
                    htmlFor="scenario-amount"
                    className="text-sm font-medium text-text-secondary"
                  >
                    Montant
                  </label>
                  <span className="font-numeric text-primary font-semibold">
                    {fmt(amount)}
                  </span>
                </div>
                <input
                  id="scenario-amount"
                  type="range"
                  min={500}
                  max={50000}
                  step={500}
                  value={amount}
                  onChange={(e) => setAmount(Number(e.target.value))}
                  className="w-full accent-primary h-1.5 rounded-full"
                />
                <div className="flex justify-between text-text-muted text-xs mt-1">
                  <span>500 €</span>
                  <span>25 000 €</span>
                  <span>50 000 €</span>
                </div>
              </div>

              {/* Delay */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label
                    htmlFor="scenario-delay"
                    className="text-sm font-medium text-text-secondary"
                  >
                    Délai
                  </label>
                  <span className="font-numeric text-primary font-semibold">
                    {delayDays} jours
                  </span>
                </div>
                <input
                  id="scenario-delay"
                  type="range"
                  min={1}
                  max={90}
                  value={delayDays}
                  onChange={(e) => setDelayDays(Number(e.target.value))}
                  className="w-full accent-primary h-1.5 rounded-full"
                />
                <div className="flex justify-between text-text-muted text-xs mt-1">
                  <span>1j</span>
                  <span>45j</span>
                  <span>90j</span>
                </div>
              </div>

              {/* Description */}
              <div>
                <label
                  htmlFor="scenario-desc"
                  className="block text-sm font-medium text-text-secondary mb-1.5"
                >
                  Description{" "}
                  <span className="text-text-muted font-normal">
                    (optionnel)
                  </span>
                </label>
                <input
                  id="scenario-desc"
                  type="text"
                  value={desc}
                  onChange={(e) => setDesc(e.target.value)}
                  placeholder="Ex. : Facture ABC retardée"
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm placeholder:text-text-muted focus:outline-none focus:border-primary/40 transition"
                />
              </div>

              {isPending && (
                <p className="text-text-muted text-xs text-center animate-pulse">
                  Calcul en cours…
                </p>
              )}
            </div>
          </Card>

          {/* ── Results panel ──────────────────────────────────────────────── */}
          <div className="space-y-4">
            {result ? (
              <>
                {/* Risk level */}
                <div
                  className={`flex items-center gap-3 px-4 py-3 rounded-xl border ${
                    result.riskLevel === "CRITICAL" ||
                    result.riskLevel === "HIGH"
                      ? "bg-danger/[0.05] border-danger/20"
                      : result.riskLevel === "MEDIUM"
                        ? "bg-warning/[0.05] border-warning/15"
                        : "bg-success/[0.05] border-success/15"
                  }`}
                >
                  <span
                    className={`text-2xl font-bold font-numeric ${riskCfg?.color}`}
                  >
                    {riskCfg?.label ?? result.riskLevel}
                  </span>
                  <div className="flex-1">
                    <p className="text-text-secondary text-sm">
                      Niveau de risque détecté
                    </p>
                  </div>
                  {(result.riskLevel === "HIGH" ||
                    result.riskLevel === "CRITICAL") && (
                    <Link to="/flash-credit">
                      <Button
                        variant="gradient"
                        size="sm"
                        leftIcon={<Zap size={13} />}
                      >
                        Activer la Réserve
                      </Button>
                    </Link>
                  )}
                </div>

                {/* KPIs */}
                <div className="grid grid-cols-2 gap-3">
                  {[
                    {
                      label: "Impact maximal",
                      value: fmt(result.maxImpact),
                      color: "text-danger",
                    },
                    {
                      label: "Solde minimum prévu",
                      value: fmt(result.minBalance),
                      color:
                        result.minBalance < 0 ? "text-danger" : "text-warning",
                    },
                    {
                      label: "Pire déficit",
                      value: fmt(result.worstDeficit),
                      color: "text-danger",
                    },
                    {
                      label: "Jours avant impact",
                      value: `${result.daysUntilImpact}j`,
                      color: "text-warning",
                    },
                  ].map((kpi) => (
                    <Card key={kpi.label} padding="sm">
                      <p className="text-text-muted text-xs mb-1 flex items-center gap-1">
                        {kpi.label}
                        {kpi.label === "Impact maximal" && (
                          <HelpTooltip text="Montant maximum que ce scénario pourrait coûter à votre trésorerie." />
                        )}
                        {kpi.label === "Solde minimum prévu" && (
                          <HelpTooltip text="Point bas de votre trésorerie si ce scénario se réalise — un solde négatif = déficit." />
                        )}
                        {kpi.label === "Pire déficit" && (
                          <HelpTooltip text="Valeur du solde le plus négatif dans la simulation. Activez la Réserve pour le couvrir." />
                        )}
                        {kpi.label === "Jours avant impact" && (
                          <HelpTooltip text="Dans combien de jours la trésorerie commence à être négativement affectée par ce scénario." />
                        )}
                      </p>
                      <p
                        className={`font-numeric font-bold text-lg ${kpi.color}`}
                      >
                        {kpi.value}
                      </p>
                    </Card>
                  ))}
                </div>

                {/* Dual chart */}
                {chartData.length > 0 && (
                  <Card>
                    <CardHeader
                      title="Impact visuel"
                      subtitle="Bleu = trajectoire de base · Violet = avec scénario"
                      helpTooltip={
                        <HelpTooltip text="Comparaison graphique de votre trajectoire de trésorerie de base vs la trajectoire avec le scénario simulé." />
                      }
                    />
                    <div className="mt-3" style={{ height: 220 }}>
                      <ResponsiveContainer width="100%" height="100%">
                        <ComposedChart
                          data={chartData}
                          margin={{ top: 4, right: 8, bottom: 0, left: 0 }}
                        >
                          <XAxis
                            dataKey="date"
                            tickFormatter={(v) =>
                              format(
                                v instanceof Date ? v : new Date(v),
                                "d MMM",
                                { locale: fr },
                              )
                            }
                            tick={{ fill: "#A0AEC0", fontSize: 10 }}
                            axisLine={false}
                            tickLine={false}
                          />
                          <YAxis
                            tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
                            tick={{ fill: "#A0AEC0", fontSize: 10 }}
                            axisLine={false}
                            tickLine={false}
                            width={36}
                          />
                          <Tooltip
                            contentStyle={{
                              background: "#1B254B",
                              border: "1px solid rgba(255,255,255,0.08)",
                              borderRadius: 12,
                              fontSize: 12,
                            }}
                            formatter={(v: number, name: string) => [
                              fmt(v),
                              name === "base" ? "Base" : "Avec scénario",
                            ]}
                            labelFormatter={(l) =>
                              format(
                                l instanceof Date ? l : new Date(l),
                                "d MMMM",
                                { locale: fr },
                              )
                            }
                          />
                          <ReferenceLine
                            y={200}
                            stroke="#EF4444"
                            strokeDasharray="4 2"
                            strokeWidth={1}
                          />
                          <Line
                            dataKey="base"
                            stroke="#06B6D4"
                            strokeWidth={2}
                            dot={false}
                            name="base"
                          />
                          <Line
                            dataKey="scenario"
                            stroke="#A78BFA"
                            strokeWidth={2}
                            dot={false}
                            name="scenario"
                            strokeDasharray="6 3"
                          />
                          <Legend
                            formatter={(v) =>
                              v === "base"
                                ? "Trajectoire de base"
                                : "Avec scénario"
                            }
                            wrapperStyle={{ fontSize: 11, color: "#A0AEC0" }}
                          />
                        </ComposedChart>
                      </ResponsiveContainer>
                    </div>
                  </Card>
                )}

                {/* Recommendation */}
                {result.recommendation && (
                  <Card>
                    <div className="flex items-start gap-3">
                      <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary flex-shrink-0 mt-0.5">
                        <Info size={16} />
                      </div>
                      <div>
                        <p className="text-white font-medium text-sm mb-1">
                          Recommandation IA
                        </p>
                        <p className="text-text-secondary text-sm leading-relaxed">
                          {result.recommendation}
                        </p>
                      </div>
                    </div>
                  </Card>
                )}
              </>
            ) : (
              <div className="flex items-center justify-center py-24">
                <div className="text-center">
                  {isPending ? (
                    <p className="text-text-muted text-sm animate-pulse">
                      Calcul en cours…
                    </p>
                  ) : (
                    <>
                      <p className="text-text-muted mb-2">
                        Ajustez les paramètres
                      </p>
                      <p className="text-text-muted text-sm">
                        Les résultats apparaîtront ici en temps réel
                      </p>
                    </>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default ScenarioPage;
