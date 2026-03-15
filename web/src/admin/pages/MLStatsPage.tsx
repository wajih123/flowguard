import React, { useState } from "react";
import {
  Brain,
  RefreshCw,
  TrendingDown,
  Users,
  CheckCircle2,
  XCircle,
  Clock,
  AlertTriangle,
  Zap,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from "recharts";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import {
  mlApi,
  MlModelVersion,
  MlRetrainEvent,
  MlQualityEntry,
} from "@/api/ml";

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmtMAE = (v: number | null) => (v == null ? "—" : `${v.toFixed(1)} €`);

const fmtPct = (v: number | null) =>
  v == null ? "—" : `${(v * 100).toFixed(1)}%`;

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString("fr-FR", {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
  });

const fmtDateTime = (iso: string | null) =>
  iso
    ? new Date(iso).toLocaleString("fr-FR", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";

const fmtDuration = (min: number | null) =>
  min == null ? "—" : `${Math.round(min)} min`;

// ── Status badge ──────────────────────────────────────────────────────────────

const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const map: Record<string, string> = {
    ACTIVE: "bg-success/20 text-success",
    CANDIDATE: "bg-primary/20 text-primary",
    DEPRECATED: "bg-white/10 text-text-secondary",
    SUCCESS: "bg-success/20 text-success",
    FAILED: "bg-danger/20 text-danger",
    RUNNING: "bg-warning/20 text-warning",
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${map[status] ?? "bg-white/10 text-text-secondary"}`}
    >
      {status}
    </span>
  );
};

// ── Active model card ─────────────────────────────────────────────────────────

const ActiveModelCard: React.FC<{ model: MlModelVersion }> = ({ model }) => (
  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
    {[
      {
        label: "MAE 7 jours",
        value: fmtMAE(model.mae_7d),
        icon: <TrendingDown size={18} />,
        color:
          model.mae_7d != null && model.mae_7d < 100
            ? "text-success"
            : "text-warning",
      },
      {
        label: "MAE 30 jours",
        value: fmtMAE(model.mae_30d),
        icon: <TrendingDown size={18} />,
        color:
          model.mae_30d != null && model.mae_30d < 150
            ? "text-success"
            : "text-warning",
      },
      {
        label: "MAE 90 jours",
        value: fmtMAE(model.mae_90d),
        icon: <TrendingDown size={18} />,
        color: "text-text-secondary",
      },
      {
        label: "Utilisateurs entraînés",
        value:
          model.n_users_trained == null
            ? "—"
            : model.n_users_trained.toLocaleString("fr-FR"),
        icon: <Users size={18} />,
        color: "text-primary",
      },
      {
        label: "Recall déficit",
        value: fmtPct(model.deficit_recall),
        icon: <CheckCircle2 size={18} />,
        color:
          model.deficit_recall == null || model.deficit_recall <= 0.7
            ? "text-warning"
            : "text-success",
      },
      {
        label: "Précision déficit",
        value: fmtPct(model.deficit_precision),
        icon: <CheckCircle2 size={18} />,
        color:
          model.deficit_precision == null || model.deficit_precision <= 0.7
            ? "text-warning"
            : "text-success",
      },
      {
        label: "Version",
        value: model.version,
        icon: <Brain size={18} />,
        color: "text-primary",
      },
      {
        label: "Entraîné le",
        value: fmtDate(model.created_at),
        icon: <Clock size={18} />,
        color: "text-text-secondary",
      },
    ].map((item) => (
      <div key={item.label} className="stat-card flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <span className="text-text-secondary text-xs">{item.label}</span>
          <span className={item.color}>{item.icon}</span>
        </div>
        <p className="text-xl font-bold text-white">{item.value}</p>
      </div>
    ))}
  </div>
);

// ── MAE evolution chart ───────────────────────────────────────────────────────

const MAEChart: React.FC<{ history: MlModelVersion[] }> = ({ history }) => {
  // Reverse so oldest is on the left
  const data = [...history]
    .reverse()
    .map((v) => ({
      name: v.version,
      "MAE 7j": v.mae_7d == null ? null : Math.round(v.mae_7d),
      "MAE 30j": v.mae_30d == null ? null : Math.round(v.mae_30d),
      "MAE 90j": v.mae_90d == null ? null : Math.round(v.mae_90d),
      active: v.status === "ACTIVE",
    }))
    .filter((d) => d["MAE 7j"] !== null || d["MAE 30j"] !== null);

  if (data.length === 0) {
    return (
      <p className="text-text-secondary text-sm text-center py-8">
        Pas encore de données — le premier entraînement est en cours.
      </p>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis dataKey="name" tick={{ fill: "#9ca3af", fontSize: 11 }} />
        <YAxis tick={{ fill: "#9ca3af", fontSize: 11 }} unit=" €" width={55} />
        <Tooltip
          contentStyle={{
            background: "#1e2230",
            border: "1px solid rgba(255,255,255,0.1)",
            borderRadius: 8,
          }}
          labelStyle={{ color: "#fff" }}
          formatter={(val: number) => `${val} €`}
        />
        <Legend wrapperStyle={{ color: "#9ca3af", fontSize: 12 }} />
        <ReferenceLine
          y={150}
          stroke="#f59e0b"
          strokeDasharray="4 2"
          label={{ value: "Seuil 150€", fill: "#f59e0b", fontSize: 10 }}
        />
        <Line
          type="monotone"
          dataKey="MAE 7j"
          stroke="#6366f1"
          strokeWidth={2}
          dot={{ r: 4 }}
          activeDot={{ r: 6 }}
          connectNulls
        />
        <Line
          type="monotone"
          dataKey="MAE 30j"
          stroke="#22c55e"
          strokeWidth={2}
          dot={{ r: 4 }}
          activeDot={{ r: 6 }}
          connectNulls
        />
        <Line
          type="monotone"
          dataKey="MAE 90j"
          stroke="#94a3b8"
          strokeWidth={1.5}
          strokeDasharray="4 2"
          dot={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  );
};

// ── Drift quality chart ───────────────────────────────────────────────────────

const DriftChart: React.FC<{ log: MlQualityEntry[] }> = ({ log }) => {
  const data = [...log].reverse().map((e) => ({
    date: e.log_date?.slice(5) ?? "", // MM-DD
    "MAE 7j": e.mae_7d == null ? null : Math.round(e.mae_7d),
    "MAE 30j": e.mae_30d == null ? null : Math.round(e.mae_30d),
    alert: e.alert_triggered ? 1 : 0,
  }));

  if (data.length === 0) {
    return (
      <p className="text-text-secondary text-sm text-center py-8">
        Pas encore de logs de qualité.
      </p>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={200}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis dataKey="date" tick={{ fill: "#9ca3af", fontSize: 10 }} />
        <YAxis tick={{ fill: "#9ca3af", fontSize: 11 }} unit=" €" width={55} />
        <Tooltip
          contentStyle={{
            background: "#1e2230",
            border: "1px solid rgba(255,255,255,0.1)",
            borderRadius: 8,
          }}
          labelStyle={{ color: "#fff" }}
          formatter={(val: number) => `${val} €`}
        />
        <Legend wrapperStyle={{ color: "#9ca3af", fontSize: 12 }} />
        <Line
          type="monotone"
          dataKey="MAE 7j"
          stroke="#6366f1"
          strokeWidth={1.5}
          dot={false}
          connectNulls
        />
        <Line
          type="monotone"
          dataKey="MAE 30j"
          stroke="#22c55e"
          strokeWidth={1.5}
          dot={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  );
};

// ── Retrain row ───────────────────────────────────────────────────────────────

const RetrainRow: React.FC<{ event: MlRetrainEvent; isLast: boolean }> = ({
  event,
  isLast,
}) => (
  <tr className={`${isLast ? "" : "border-b border-white/[0.04]"}`}>
    <td className="py-3 pr-4 text-sm text-text-secondary">
      {fmtDateTime(event.started_at)}
    </td>
    <td className="py-3 pr-4 text-sm text-white">{event.reason ?? "Manuel"}</td>
    <td className="py-3 pr-4">
      <StatusBadge status={event.status} />
    </td>
    <td className="py-3 pr-4 text-sm text-white">
      {event.n_users == null ? "—" : event.n_users.toLocaleString("fr-FR")}
    </td>
    <td className="py-3 pr-4 text-sm text-white">{fmtMAE(event.final_mae)}</td>
    <td className="py-3 text-sm text-text-secondary">
      {fmtDuration(event.duration_min)}
    </td>
  </tr>
);

// ── Model history row ─────────────────────────────────────────────────────────

const ModelRow: React.FC<{ model: MlModelVersion; isLast: boolean }> = ({
  model,
  isLast,
}) => (
  <tr className={`${isLast ? "" : "border-b border-white/[0.04]"}`}>
    <td className="py-3 pr-4 text-sm font-mono text-white">{model.version}</td>
    <td className="py-3 pr-4">
      <StatusBadge status={model.status} />
    </td>
    <td className="py-3 pr-4 text-sm text-white">{fmtMAE(model.mae_7d)}</td>
    <td className="py-3 pr-4 text-sm text-white">{fmtMAE(model.mae_30d)}</td>
    <td className="py-3 pr-4 text-sm text-white">{fmtMAE(model.mae_90d)}</td>
    <td className="py-3 pr-4 text-sm text-white">
      {model.n_users_trained == null
        ? "—"
        : model.n_users_trained.toLocaleString("fr-FR")}
    </td>
    <td className="py-3 text-sm text-text-secondary">
      {fmtDate(model.created_at)}
    </td>
  </tr>
);

// ── Main page ─────────────────────────────────────────────────────────────────

const MLStatsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [retrainFeedback, setRetrainFeedback] = useState<string | null>(null);

  const {
    data: stats,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["admin", "ml", "stats"],
    queryFn: mlApi.getStats,
    staleTime: 2 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
  });

  const { mutate: triggerRetrain, isPending: retraining } = useMutation({
    mutationFn: mlApi.triggerRetrain,
    onSuccess: (res) => {
      setRetrainFeedback(res.message ?? res.status);
      queryClient.invalidateQueries({ queryKey: ["admin", "ml", "stats"] });
      setTimeout(() => setRetrainFeedback(null), 8000);
    },
    onError: () => {
      setRetrainFeedback("Erreur lors du déclenchement de l'entraînement.");
      setTimeout(() => setRetrainFeedback(null), 6000);
    },
  });

  return (
    <AdminLayout
      title="Intelligence Artificielle"
      subtitle="Suivi de l'entraînement et de l'amélioration du modèle"
      action={
        <Button
          variant="primary"
          size="sm"
          leftIcon={<Zap size={16} />}
          isLoading={retraining}
          onClick={() => triggerRetrain()}
        >
          Ré-entraîner maintenant
        </Button>
      }
    >
      <div className="max-w-6xl mx-auto space-y-6 animate-fade-in">
        {/* Feedback banner */}
        {retrainFeedback && (
          <div className="flex items-center gap-3 rounded-xl border border-primary/30 bg-primary/10 px-4 py-3 text-sm text-white">
            <Brain size={16} className="text-primary flex-shrink-0" />
            {retrainFeedback}
          </div>
        )}

        {isLoading && <Loader />}

        {isError && (
          <div className="flex items-center gap-3 rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-white">
            <XCircle size={16} className="text-danger flex-shrink-0" />
            Impossible de charger les données IA. Les tables ML sont peut-être
            vides (en attente du premier entraînement).
          </div>
        )}

        {stats && (
          <>
            {/* Active model */}
            <Card>
              <CardHeader
                title="Modèle actif"
                icon={<Brain size={18} />}
                action={
                  stats.activeVersion ? (
                    <StatusBadge status={stats.activeVersion.status} />
                  ) : undefined
                }
              />
              {stats.activeVersion ? (
                <div className="mt-4">
                  <ActiveModelCard model={stats.activeVersion} />
                </div>
              ) : (
                <p className="text-text-secondary text-sm mt-4">
                  Aucun modèle actif — l'entraînement initial est en cours ou
                  n'a pas encore démarré.
                </p>
              )}
            </Card>

            {/* MAE evolution chart */}
            <Card>
              <CardHeader
                title="Évolution de la précision (MAE €)"
                icon={<TrendingDown size={18} />}
                action={
                  <span className="text-xs text-text-secondary">
                    {stats.totalModels} version(s)
                  </span>
                }
              />
              <p className="text-text-secondary text-xs mt-1 mb-4">
                MAE = Erreur absolue moyenne en euros. Plus elle diminue, plus
                le modèle est précis.
              </p>
              <MAEChart history={stats.modelHistory} />
            </Card>

            {/* Daily quality drift */}
            {stats.qualityLog.length > 0 && (
              <Card>
                <CardHeader
                  title="Qualité journalière des prédictions (30 derniers jours)"
                  icon={<AlertTriangle size={18} />}
                />
                <p className="text-text-secondary text-xs mt-1 mb-4">
                  Suivi quotidien du MAE après évaluation sur les vraies
                  valeurs. Une hausse soudaine peut déclencher un
                  ré-entraînement automatique.
                </p>
                <DriftChart log={stats.qualityLog} />

                {/* Alert count */}
                {stats.qualityLog.some((q) => q.alert_triggered) && (
                  <p className="mt-3 text-xs text-warning flex items-center gap-1">
                    <AlertTriangle size={12} />
                    {
                      stats.qualityLog.filter((q) => q.alert_triggered).length
                    }{" "}
                    alerte(s) de dérive détectée(s) sur les 30 derniers jours
                  </p>
                )}
              </Card>
            )}

            {/* Retrain log */}
            <Card>
              <CardHeader
                title="Historique des entraînements"
                icon={<RefreshCw size={18} />}
                action={
                  <span className="text-xs text-text-secondary">
                    {stats.totalRetrains} entraînement(s)
                  </span>
                }
              />
              {stats.retrainLog.length === 0 ? (
                <p className="text-text-secondary text-sm mt-4">
                  Aucun entraînement enregistré pour le moment.
                </p>
              ) : (
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="border-b border-white/[0.06]">
                        {[
                          "Date",
                          "Raison",
                          "Statut",
                          "Utilisateurs",
                          "MAE final",
                          "Durée",
                        ].map((h) => (
                          <th
                            key={h}
                            className="pb-2 pr-4 text-xs text-text-secondary font-medium"
                          >
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {stats.retrainLog.map((event, i) => (
                        <RetrainRow
                          key={event.started_at}
                          event={event}
                          isLast={i === stats.retrainLog.length - 1}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            {/* Model versions table */}
            <Card>
              <CardHeader
                title="Versions du modèle"
                icon={<Brain size={18} />}
                action={
                  <span className="text-xs text-text-secondary">
                    {stats.totalModels} version(s)
                  </span>
                }
              />
              {stats.modelHistory.length === 0 ? (
                <p className="text-text-secondary text-sm mt-4">
                  Aucune version enregistrée.
                </p>
              ) : (
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="border-b border-white/[0.06]">
                        {[
                          "Version",
                          "Statut",
                          "MAE 7j",
                          "MAE 30j",
                          "MAE 90j",
                          "Utilisateurs",
                          "Date",
                        ].map((h) => (
                          <th
                            key={h}
                            className="pb-2 pr-4 text-xs text-text-secondary font-medium"
                          >
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {stats.modelHistory.map((model, i) => (
                        <ModelRow
                          key={model.version}
                          model={model}
                          isLast={i === stats.modelHistory.length - 1}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>

            {/* Explanation section */}
            <Card>
              <CardHeader
                title="Comment l'IA s'améliore-t-elle ?"
                icon={<CheckCircle2 size={18} />}
              />
              <div className="mt-4 space-y-3 text-sm text-text-secondary">
                <div className="flex gap-3">
                  <div className="w-6 h-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-primary text-xs font-bold">1</span>
                  </div>
                  <p>
                    <span className="text-white font-medium">
                      Données enrichies
                    </span>{" "}
                    — Chaque jour, de nouvelles transactions arrivent (10 826
                    transactions aujourd'hui). Le modèle apprend sur un
                    historique de plus en plus complet.
                  </p>
                </div>
                <div className="flex gap-3">
                  <div className="w-6 h-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-primary text-xs font-bold">2</span>
                  </div>
                  <p>
                    <span className="text-white font-medium">
                      Ré-entraînement automatique
                    </span>{" "}
                    — Toutes les 6h, le système vérifie si le MAE a dégradé de{" "}
                    {">"} 15 %. Si oui, un ré-entraînement se déclenche
                    automatiquement.
                  </p>
                </div>
                <div className="flex gap-3">
                  <div className="w-6 h-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-primary text-xs font-bold">3</span>
                  </div>
                  <p>
                    <span className="text-white font-medium">
                      Promotion du meilleur modèle
                    </span>{" "}
                    — Après chaque entraînement, le nouveau modèle est promu
                    ACTIF s'il bat le précédent. L'ancien passe en DEPRECATED.
                  </p>
                </div>
                <div className="flex gap-3">
                  <div className="w-6 h-6 rounded-full bg-primary/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-primary text-xs font-bold">4</span>
                  </div>
                  <p>
                    <span className="text-white font-medium">MAE cible</span> —
                    Le seuil de qualité gate est MAE 30j {"<"} 150 €. En
                    dessous, toutes les prévisions sont débloquées avec le score
                    de confiance "Élevé".
                  </p>
                </div>
              </div>
            </Card>
          </>
        )}
      </div>
    </AdminLayout>
  );
};

export default MLStatsPage;
