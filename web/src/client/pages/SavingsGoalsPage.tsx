import React, { useState } from "react";
import {
  Plus,
  Target,
  TrendingUp,
  Edit2,
  Trash2,
  CheckCircle,
  RefreshCw,
  X,
  Lightbulb,
  AlertTriangle,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Loader } from "@/components/ui/Loader";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  savingsGoalsApi,
  type CreateGoalPayload,
  type UpdateGoalPayload,
  type GoalType,
  type SavingsGoal,
} from "@/api/savingsGoals";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

// ── Formatters ────────────────────────────────────────────────────────────────

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

// ── Goal type options ─────────────────────────────────────────────────────────

const GOAL_TYPES: { value: GoalType; label: string; emoji: string }[] = [
  { value: "EMERGENCY_FUND", label: "Fonds d'urgence", emoji: "🛡️" },
  { value: "VACATION", label: "Vacances", emoji: "✈️" },
  { value: "EQUIPMENT", label: "Équipement", emoji: "💻" },
  { value: "REAL_ESTATE", label: "Immobilier", emoji: "🏠" },
  { value: "EDUCATION", label: "Formation", emoji: "🎓" },
  { value: "RETIREMENT", label: "Retraite", emoji: "🌅" },
  { value: "PROJECT", label: "Projet", emoji: "🚀" },
  { value: "OTHER", label: "Autre objectif", emoji: "🎯" },
];

// ── Progress circle ───────────────────────────────────────────────────────────

const CircleProgress: React.FC<{ percent: number; size?: number }> = ({
  percent,
  size = 80,
}) => {
  const r = size / 2 - 8;
  const circ = 2 * Math.PI * r;
  const capped = Math.min(percent, 100);
  const dashOffset = circ - (capped / 100) * circ;
  const cx = size / 2;
  return (
    <svg
      width={size}
      height={size}
      className="rotate-[-90deg] shrink-0"
      aria-hidden="true"
    >
      <circle
        cx={cx}
        cy={cx}
        r={r}
        fill="none"
        stroke="rgba(255,255,255,0.07)"
        strokeWidth="7"
      />
      <circle
        cx={cx}
        cy={cx}
        r={r}
        fill="none"
        stroke={
          capped >= 100 ? "var(--color-success)" : "var(--color-primary)"
        }
        strokeWidth="7"
        strokeLinecap="round"
        strokeDasharray={circ}
        strokeDashoffset={dashOffset}
        className="transition-all duration-700"
      />
    </svg>
  );
};

// ── Goal card ─────────────────────────────────────────────────────────────────

const GoalCard: React.FC<{
  goal: SavingsGoal;
  onEdit: (g: SavingsGoal) => void;
  onDelete: (id: string) => void;
  deleting: boolean;
}> = ({ goal, onEdit, onDelete, deleting }) => {
  const pct = Math.round(goal.progressPercent);
  const reached = pct >= 100;
  const monthsLeft =
    goal.estimatedDaysToReach > 0
      ? Math.ceil(goal.estimatedDaysToReach / 30)
      : null;
  const isAtRisk =
    goal.targetDate && goal.estimatedDate
      ? new Date(goal.estimatedDate) > new Date(goal.targetDate)
      : false;

  return (
    <Card padding="md" className="flex flex-col gap-3">
      {/* Header row */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <span className="text-2xl leading-none">{goal.goalTypeEmoji}</span>
          <div>
            <p className="text-white text-sm font-semibold leading-tight">
              {goal.label}
            </p>
            <p className="text-text-muted text-xs">{goal.goalTypeLabel}</p>
          </div>
        </div>
        <div className="flex gap-1.5 shrink-0">
          <button
            onClick={() => onEdit(goal)}
            className="p-1.5 rounded-lg hover:bg-white/[0.07] text-text-muted hover:text-white transition"
            aria-label="Modifier"
          >
            <Edit2 size={13} />
          </button>
          <button
            onClick={() => onDelete(goal.id)}
            disabled={deleting}
            className="p-1.5 rounded-lg hover:bg-danger/15 text-text-muted hover:text-danger transition disabled:opacity-40"
            aria-label="Supprimer"
          >
            <Trash2 size={13} />
          </button>
        </div>
      </div>

      {/* Progress row */}
      <div className="flex items-center gap-4">
        <div className="relative shrink-0">
          <CircleProgress percent={pct} size={72} />
          <div className="absolute inset-0 flex items-center justify-center">
            <span
              className={`font-numeric text-sm font-bold ${reached ? "text-success" : "text-white"}`}
            >
              {pct}%
            </span>
          </div>
        </div>
        <div className="flex-1 min-w-0 space-y-1.5">
          <div className="flex justify-between text-xs">
            <span className="text-text-muted">Actuel</span>
            <span className="font-numeric text-white">
              {fmt(goal.currentBalance)}
            </span>
          </div>
          <div className="flex justify-between text-xs">
            <span className="text-text-muted">Objectif</span>
            <span className="font-numeric text-white">
              {fmt(goal.targetAmount)}
            </span>
          </div>
          <div className="flex justify-between text-xs">
            <span className="text-text-muted">Manquant</span>
            <span className="font-numeric text-warning font-medium">
              {fmt(Math.max(goal.targetAmount - goal.currentBalance, 0))}
            </span>
          </div>
        </div>
      </div>

      {/* Target date / ETA row */}
      {!reached && (
        <div
          className={`flex items-center gap-2 text-xs px-3 py-2 rounded-lg ${
            isAtRisk
              ? "bg-warning/[0.08] border border-warning/20 text-warning"
              : "bg-white/[0.04] text-text-secondary"
          }`}
        >
          {isAtRisk ? (
            <AlertTriangle size={12} className="shrink-0" />
          ) : (
            <TrendingUp size={12} className="shrink-0 text-primary" />
          )}
          <span>
            {isAtRisk
              ? `Retard prévu : atteinte estimée ${goal.estimatedDate ? format(parseISO(goal.estimatedDate), "MMM yyyy", { locale: fr }) : "?"}, objectif ${goal.targetDate ? format(parseISO(goal.targetDate), "MMM yyyy", { locale: fr }) : "?"}`
              : monthsLeft
                ? `Atteinte estimée dans ${monthsLeft} mois · ${fmt(goal.recommendedMonthly)}/mois recommandé`
                : `Épargne de ${fmt(goal.recommendedMonthly)}/mois recommandée`}
          </span>
        </div>
      )}

      {reached && (
        <div className="flex items-center gap-2 text-xs px-3 py-2 rounded-lg bg-success/10 border border-success/20 text-success">
          <CheckCircle size={12} />
          <span>Objectif atteint ! Félicitations</span>
        </div>
      )}

      {/* Coach tip */}
      {goal.coachTip && !reached && (
        <div className="flex items-start gap-2 text-xs px-3 py-2 rounded-lg bg-primary/[0.07] border border-primary/15 text-text-secondary">
          <Lightbulb size={12} className="shrink-0 text-primary mt-0.5" />
          <span>{goal.coachTip}</span>
        </div>
      )}
    </Card>
  );
};

// ── Goal form modal ───────────────────────────────────────────────────────────

interface GoalFormProps {
  initial?: SavingsGoal;
  onSubmit: (p: CreateGoalPayload | UpdateGoalPayload) => void;
  onClose: () => void;
  loading: boolean;
}

const GoalForm: React.FC<GoalFormProps> = ({
  initial,
  onSubmit,
  onClose,
  loading,
}) => {
  const [goalType, setGoalType] = useState<GoalType>(
    initial?.goalType ?? "OTHER",
  );
  const [label, setLabel] = useState(initial?.label ?? "");
  const [amount, setAmount] = useState(
    initial?.targetAmount ? String(initial.targetAmount) : "",
  );
  const [targetDate, setTargetDate] = useState(initial?.targetDate ?? "");
  const [monthly, setMonthly] = useState(
    initial?.monthlyContribution ? String(initial.monthlyContribution) : "",
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseFloat(amount.replace(",", ".").replace(/\s/g, ""));
    if (!parsed || parsed < 1) return;
    const selectedType = GOAL_TYPES.find((t) => t.value === goalType)!;
    onSubmit({
      goalType,
      label: label.trim() || selectedType.label,
      targetAmount: parsed,
      targetDate: targetDate || undefined,
      monthlyContribution: monthly
        ? parseFloat(monthly.replace(",", "."))
        : undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="w-full max-w-md bg-surface-elevated rounded-2xl border border-white/[0.08] shadow-2xl animate-slide-up">
        <div className="flex items-center justify-between p-5 border-b border-white/[0.06]">
          <h2
            className="text-white text-base font-bold"
            style={{ fontFamily: "var(--font-display)" }}
          >
            {initial ? "Modifier l'objectif" : "Nouvel objectif"}
          </h2>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-white/[0.07] text-text-muted hover:text-white transition"
          >
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {/* Goal type selector */}
          <div>
            <label className="text-text-secondary text-xs mb-2 block">
              Type d'objectif
            </label>
            <div className="grid grid-cols-4 gap-1.5">
              {GOAL_TYPES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  onClick={() => setGoalType(t.value)}
                  className={`flex flex-col items-center gap-1 p-2 rounded-xl border text-center transition-all ${
                    goalType === t.value
                      ? "border-primary/50 bg-primary/10 text-white"
                      : "border-white/[0.07] bg-white/[0.03] text-text-muted hover:border-white/[0.15]"
                  }`}
                >
                  <span className="text-lg leading-none">{t.emoji}</span>
                  <span className="text-[10px] leading-tight font-medium">
                    {t.label}
                  </span>
                </button>
              ))}
            </div>
          </div>

          {/* Label */}
          <div>
            <label className="text-text-secondary text-xs mb-1.5 block">
              Nom personnalisé (optionnel)
            </label>
            <input
              type="text"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder={
                GOAL_TYPES.find((t) => t.value === goalType)?.label ?? ""
              }
              className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted focus:outline-none focus:border-primary/60 transition"
              maxLength={100}
            />
          </div>

          {/* Amount */}
          <div>
            <label className="text-text-secondary text-xs mb-1.5 block">
              Montant cible (€) *
            </label>
            <input
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Ex : 5000"
              min="1"
              step="1"
              required
              className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted font-numeric focus:outline-none focus:border-primary/60 transition"
            />
          </div>

          {/* Target date */}
          <div>
            <label className="text-text-secondary text-xs mb-1.5 block">
              Date cible (optionnel)
            </label>
            <input
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              min={new Date().toISOString().split("T")[0]}
              className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted focus:outline-none focus:border-primary/60 transition"
            />
          </div>

          {/* Monthly contribution */}
          <div>
            <label className="text-text-secondary text-xs mb-1.5 block">
              Épargne mensuelle souhaitée (€, optionnel)
            </label>
            <input
              type="number"
              value={monthly}
              onChange={(e) => setMonthly(e.target.value)}
              placeholder="Laissez vide pour la recommandation auto"
              min="0"
              step="1"
              className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted font-numeric focus:outline-none focus:border-primary/60 transition"
            />
          </div>

          <div className="flex gap-2 pt-1">
            <button
              type="submit"
              disabled={loading}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-primary hover:bg-primary/90 disabled:opacity-50 text-white text-sm font-medium rounded-lg transition"
            >
              {loading ? (
                <RefreshCw size={14} className="animate-spin" />
              ) : (
                <CheckCircle size={14} />
              )}
              {initial ? "Mettre à jour" : "Créer l'objectif"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 bg-white/[0.05] hover:bg-white/[0.08] text-text-muted hover:text-white text-sm rounded-lg transition"
            >
              Annuler
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────

const SavingsGoalsPage: React.FC = () => {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editTarget, setEditTarget] = useState<SavingsGoal | null>(null);

  const { data: goals, isLoading } = useQuery({
    queryKey: ["savings-goals"],
    queryFn: savingsGoalsApi.list,
    staleTime: 60_000,
  });

  const createMut = useMutation({
    mutationFn: savingsGoalsApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["savings-goals"] });
      setShowForm(false);
    },
  });

  const updateMut = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateGoalPayload }) =>
      savingsGoalsApi.update(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["savings-goals"] });
      setEditTarget(null);
    },
  });

  const deleteMut = useMutation({
    mutationFn: savingsGoalsApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["savings-goals"] }),
  });

  const handleDelete = (id: string) => {
    if (window.confirm("Supprimer cet objectif ?")) deleteMut.mutate(id);
  };

  const totalProgress =
    goals && goals.length > 0
      ? goals.reduce((sum, g) => sum + g.progressPercent, 0) / goals.length
      : 0;

  return (
    <Layout title="Objectifs d'épargne">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Objectifs d'épargne
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Définissez plusieurs objectifs et suivez votre progression en
              temps réel
            </p>
          </div>
          <Button
            variant="gradient"
            size="sm"
            leftIcon={<Plus size={14} />}
            onClick={() => setShowForm(true)}
          >
            Nouvel objectif
          </Button>
        </div>

        {/* Summary bar when multiple goals */}
        {goals && goals.length > 1 && (
          <Card padding="md">
            <div className="flex items-center gap-4">
              <Target size={18} className="text-primary shrink-0" />
              <div className="flex-1">
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-text-secondary">
                    Progression moyenne — {goals.length} objectifs
                  </span>
                  <span className="font-numeric text-white font-medium">
                    {Math.round(totalProgress)}%
                  </span>
                </div>
                <div className="w-full h-1.5 rounded-full bg-white/[0.07]">
                  <div
                    className="h-full rounded-full bg-primary transition-all duration-700"
                    style={{ width: `${Math.min(totalProgress, 100)}%` }}
                  />
                </div>
              </div>
            </div>
          </Card>
        )}

        {/* Goals grid */}
        {isLoading ? (
          <Loader text="Chargement…" />
        ) : goals && goals.length > 0 ? (
          <div className="grid gap-4 sm:grid-cols-2">
            {goals.map((g) => (
              <GoalCard
                key={g.id}
                goal={g}
                onEdit={(g) => setEditTarget(g)}
                onDelete={handleDelete}
                deleting={deleteMut.isPending}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            icon={<Target size={28} />}
            title="Aucun objectif d'épargne"
            description="Fonds d'urgence, vacances, équipement… définissez vos objectifs et laissez FlowGuard vous aider à les atteindre."
            action={
              <Button
                variant="gradient"
                leftIcon={<Plus size={14} />}
                onClick={() => setShowForm(true)}
              >
                Créer mon premier objectif
              </Button>
            }
          />
        )}

        {/* Tip card */}
        {goals && goals.length > 0 && (
          <div className="flex items-start gap-3 p-4 rounded-xl bg-primary/[0.06] border border-primary/15">
            <Lightbulb size={16} className="text-primary shrink-0 mt-0.5" />
            <p className="text-text-secondary text-xs leading-relaxed">
              La progression est calculée sur le solde total de vos comptes
              actifs, réparti équitablement entre vos objectifs. Les conseils du
              coach s'adaptent à vos habitudes de dépenses des 3 derniers mois.
            </p>
          </div>
        )}
      </div>

      {/* Create form modal */}
      {showForm && (
        <GoalForm
          onSubmit={(p) => createMut.mutate(p as CreateGoalPayload)}
          onClose={() => setShowForm(false)}
          loading={createMut.isPending}
        />
      )}

      {/* Edit form modal */}
      {editTarget && (
        <GoalForm
          initial={editTarget}
          onSubmit={(p) =>
            updateMut.mutate({ id: editTarget.id, payload: p as UpdateGoalPayload })
          }
          onClose={() => setEditTarget(null)}
          loading={updateMut.isPending}
        />
      )}
    </Layout>
  );
};

export default SavingsGoalsPage;
