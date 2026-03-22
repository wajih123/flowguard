import React, { useState } from "react";
import {
  Target,
  TrendingUp,
  Edit2,
  Trash2,
  CheckCircle,
  RefreshCw,
  Plus,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { cashGoalApi } from "@/api/cashGoal";
import type { UpsertGoalPayload } from "@/api/cashGoal";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(n);

interface GoalFormProps {
  initial?: UpsertGoalPayload;
  onSubmit: (payload: UpsertGoalPayload) => void;
  onCancel?: () => void;
  loading?: boolean;
}

const GoalForm: React.FC<GoalFormProps> = ({
  initial,
  onSubmit,
  onCancel,
  loading,
}) => {
  const [label, setLabel] = useState(initial?.label ?? "");
  const [amount, setAmount] = useState(
    initial?.targetAmount ? String(initial.targetAmount) : "",
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseFloat(amount.replace(",", ".").replace(/\s/g, ""));
    if (!parsed || parsed < 1) return;
    onSubmit({ targetAmount: parsed, label: label.trim() || "Mon objectif" });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="text-text-secondary text-xs mb-1.5 block">
          Nom de l'objectif
        </label>
        <input
          type="text"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="Ex : Fonds de roulement 3 mois"
          className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted focus:outline-none focus:border-primary/60 transition"
          maxLength={100}
        />
      </div>
      <div>
        <label className="text-text-secondary text-xs mb-1.5 block">
          Montant cible (€)
        </label>
        <input
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="Ex : 15000"
          min="1"
          step="1"
          required
          className="w-full bg-white/[0.05] border border-white/[0.1] rounded-lg px-3 py-2.5 text-sm text-white placeholder-text-muted font-numeric focus:outline-none focus:border-primary/60 transition"
        />
      </div>
      <div className="flex gap-2">
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
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2.5 bg-white/[0.05] hover:bg-white/[0.08] text-text-muted hover:text-white text-sm rounded-lg transition"
          >
            Annuler
          </button>
        )}
      </div>
    </form>
  );
};

const CircleProgress: React.FC<{ percent: number }> = ({ percent }) => {
  const r = 54;
  const circ = 2 * Math.PI * r;
  const capped = Math.min(percent, 100);
  const dashOffset = circ - (capped / 100) * circ;
  return (
    <svg width="130" height="130" className="rotate-[-90deg]">
      <circle
        cx="65"
        cy="65"
        r={r}
        fill="none"
        stroke="rgba(255,255,255,0.07)"
        strokeWidth="10"
      />
      <circle
        cx="65"
        cy="65"
        r={r}
        fill="none"
        stroke={capped >= 100 ? "var(--color-success)" : "var(--color-primary)"}
        strokeWidth="10"
        strokeLinecap="round"
        strokeDasharray={circ}
        strokeDashoffset={dashOffset}
        className="transition-all duration-700"
      />
    </svg>
  );
};

const CashGoalPage: React.FC = () => {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);

  const { data: goal, isLoading } = useQuery({
    queryKey: ["cash-goal"],
    queryFn: cashGoalApi.get,
    staleTime: 60 * 1000,
  });

  const upsertMut = useMutation({
    mutationFn: cashGoalApi.upsert,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cash-goal"] });
      setEditing(false);
    },
  });

  const deleteMut = useMutation({
    mutationFn: cashGoalApi.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cash-goal"] });
    },
  });

  const handleDelete = () => {
    if (window.confirm("Supprimer l'objectif ?")) {
      deleteMut.mutate();
    }
  };

  return (
    <Layout title="Objectif épargne">
      <div className="max-w-2xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Objectif épargne
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Définissez une réserve de trésorerie cible et suivez votre
              progression
            </p>
          </div>
        </div>

        {isLoading ? (
          <Card padding="lg">
            <Loader text="Chargement…" />
          </Card>
        ) : goal && !editing ? (
          <>
            {/* Progress card */}
            <Card padding="lg">
              <div className="flex flex-col items-center gap-2">
                <div className="relative">
                  <CircleProgress percent={goal.progressPercent} />
                  <div
                    className="absolute inset-0 flex flex-col items-center justify-center"
                    style={{ rotate: "0deg" }}
                  >
                    <p
                      className={`font-numeric text-2xl font-bold ${
                        goal.progressPercent >= 100
                          ? "text-success"
                          : "text-white"
                      }`}
                    >
                      {Math.round(goal.progressPercent)}%
                    </p>
                    {goal.progressPercent >= 100 && (
                      <CheckCircle size={14} className="text-success mt-1" />
                    )}
                  </div>
                </div>
                <p className="text-white text-base font-semibold">
                  {goal.label}
                </p>
                <div className="flex gap-6 mt-2">
                  <div className="text-center">
                    <p className="text-text-muted text-xs">Actuel</p>
                    <p className="font-numeric text-white font-medium text-sm">
                      {fmt(goal.currentBalance)}
                    </p>
                  </div>
                  <div className="w-px bg-white/[0.07]" />
                  <div className="text-center">
                    <p className="text-text-muted text-xs">Objectif</p>
                    <p className="font-numeric text-white font-medium text-sm">
                      {fmt(goal.targetAmount)}
                    </p>
                  </div>
                  <div className="w-px bg-white/[0.07]" />
                  <div className="text-center">
                    <p className="text-text-muted text-xs">Manquant</p>
                    <p className="font-numeric text-warning font-medium text-sm">
                      {fmt(
                        Math.max(goal.targetAmount - goal.currentBalance, 0),
                      )}
                    </p>
                  </div>
                </div>
              </div>
            </Card>

            {/* Estimate card */}
            {goal.progressPercent < 100 && goal.estimatedDate && (
              <Card padding="md">
                <div className="flex items-center gap-3">
                  <TrendingUp size={18} className="text-primary shrink-0" />
                  <div>
                    <p className="text-white text-sm font-medium">
                      Date d'atteinte estimée
                    </p>
                    <p className="text-text-secondary text-xs mt-0.5">
                      {format(parseISO(goal.estimatedDate), "MMMM yyyy", {
                        locale: fr,
                      })}{" "}
                      · dans {Math.ceil(goal.estimatedDaysToReach / 30)} mois
                      environ
                    </p>
                  </div>
                </div>
              </Card>
            )}

            {goal.progressPercent >= 100 && (
              <div className="flex items-center gap-3 p-4 rounded-xl border border-success/25 bg-success/10">
                <CheckCircle size={18} className="text-success shrink-0" />
                <p className="text-white text-sm font-semibold">
                  Objectif atteint ! Félicitations 🎉
                </p>
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-2">
              <button
                onClick={() => setEditing(true)}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-white/[0.05] hover:bg-white/[0.08] text-white text-sm font-medium rounded-lg transition"
              >
                <Edit2 size={14} />
                Modifier
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteMut.isPending}
                className="flex items-center gap-2 px-4 py-2.5 bg-danger/10 hover:bg-danger/20 disabled:opacity-50 text-danger text-sm font-medium rounded-lg border border-danger/20 transition"
              >
                <Trash2 size={14} />
                Supprimer
              </button>
            </div>
          </>
        ) : (
          /* Create or edit form */
          <Card padding="lg">
            <CardHeader
              title={editing ? "Modifier l'objectif" : "Créer un objectif"}
              helpTooltip={
                <HelpTooltip text="L'objectif est calculé par rapport au solde total de vos comptes connectés. La date d'atteinte est une estimation basée sur un taux d'épargne de 10%/mois." />
              }
            />
            {/* Empty state illustration */}
            {!editing && (
              <div className="flex flex-col items-center py-6 gap-3 text-text-muted">
                <div className="p-4 rounded-full bg-primary/10">
                  <Target size={28} className="text-primary" />
                </div>
                <p className="text-white text-sm font-medium">
                  Aucun objectif défini
                </p>
                <p className="text-xs text-center max-w-xs">
                  Définissez une réserve de trésorerie cible : 3 mois de
                  charges, un fonds d'urgence, ou tout autre montant
                  stratégique.
                </p>
                <div className="flex items-center gap-1.5 text-text-muted text-xs mt-1">
                  <Plus size={11} />
                  <span>Remplissez le formulaire ci-dessous</span>
                </div>
              </div>
            )}
            <div className="mt-2">
              <GoalForm
                initial={
                  editing && goal
                    ? { targetAmount: goal.targetAmount, label: goal.label }
                    : undefined
                }
                onSubmit={(p) => upsertMut.mutate(p)}
                onCancel={editing ? () => setEditing(false) : undefined}
                loading={upsertMut.isPending}
              />
            </div>
          </Card>
        )}

        <p className="text-text-muted text-xs text-center flex items-center justify-center gap-1.5">
          <CheckCircle size={11} /> La progression est calculée sur le solde
          total de vos comptes actifs connectés.
        </p>
      </div>
    </Layout>
  );
};

export default CashGoalPage;
