import React, { useState } from "react";
import { Bell, CheckCheck, Zap } from "lucide-react";
import { Link } from "react-router-dom";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loader } from "@/components/ui/Loader";
import {
  useAlerts,
  useMarkAlertRead,
  useMarkAllAlertsRead,
} from "@/hooks/useAlerts";
import { format, parseISO, isToday, isThisWeek } from "date-fns";
import { fr } from "date-fns/locale";
import type { Alert } from "@/domain/Alert";

const severityMap: Record<
  string,
  { icon: string; color: string; bg: string; border: string }
> = {
  CRITICAL: {
    icon: "🔴",
    color: "text-danger",
    bg: "bg-danger/[0.06]",
    border: "border-danger/20",
  },
  HIGH: {
    icon: "🔴",
    color: "text-danger",
    bg: "bg-danger/[0.04]",
    border: "border-danger/15",
  },
  MEDIUM: {
    icon: "🟡",
    color: "text-warning",
    bg: "bg-warning/[0.05]",
    border: "border-warning/15",
  },
  LOW: {
    icon: "🔵",
    color: "text-info",
    bg: "bg-info/[0.04]",
    border: "border-info/10",
  },
  INFO: {
    icon: "ℹ",
    color: "text-primary",
    bg: "bg-primary/[0.04]",
    border: "border-primary/10",
  },
};

const ALERT_TYPE_LABELS: Record<string, string> = {
  CASH_SHORTAGE: "Manque de liquidités",
  UNUSUAL_SPEND: "Dépense inhabituelle",
  PAYMENT_DUE: "Paiement à venir",
  POSITIVE_TREND: "Tendance positive",
};

const fmtEuro = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const AlertRow: React.FC<{
  alert: Alert;
  onRead: (id: string) => void;
  loading: boolean;
}> = ({ alert, onRead, loading }) => {
  const cfg = severityMap[alert.severity] ?? severityMap.INFO;
  const isCritical = alert.severity === "CRITICAL" || alert.severity === "HIGH";

  return (
    <div
      className={`
        relative rounded-xl border p-4 transition-all duration-200
        ${!alert.isRead ? `${cfg.bg} ${cfg.border}` : "border-white/[0.04] bg-transparent"}
      `}
    >
      {/* Unread indicator dot */}
      {!alert.isRead && (
        <span className="absolute top-3 right-3 w-2 h-2 rounded-full bg-primary animate-pulse-dot" />
      )}

      <div className="flex items-start gap-3">
        <span className="text-base leading-none mt-0.5 flex-shrink-0">
          {cfg.icon}
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <span
              className={`text-xs font-semibold uppercase tracking-wide ${cfg.color}`}
              style={{ fontFamily: "var(--font-display)" }}
            >
              {ALERT_TYPE_LABELS[alert.type] ?? alert.type}
            </span>
          </div>
          <p className="text-white text-sm leading-relaxed">{alert.message}</p>
          {alert.projectedDeficit && (
            <p className="font-numeric text-danger text-xs mt-1 font-medium">
              Déficit prévu : {fmtEuro(alert.projectedDeficit)}
            </p>
          )}
          <p className="text-text-muted text-xs mt-1.5">
            {format(parseISO(alert.createdAt), "d MMM yyyy 'à' HH:mm", {
              locale: fr,
            })}
          </p>
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-2 mt-3">
        {isCritical && (
          <Link to="/flash-credit">
            <Button variant="gradient" size="sm" leftIcon={<Zap size={13} />}>
              Activer la Réserve
            </Button>
          </Link>
        )}
        {!alert.isRead && (
          <Button
            variant="ghost"
            size="sm"
            isLoading={loading}
            onClick={() => onRead(alert.id)}
          >
            Marquer comme lu
          </Button>
        )}
      </div>
    </div>
  );
};

/** Group alerts: today / this week / older */
const groupAlerts = (alerts: Alert[]) => {
  const today: Alert[] = [];
  const week: Alert[] = [];
  const older: Alert[] = [];
  for (const a of alerts) {
    const d = parseISO(a.createdAt);
    if (isToday(d)) today.push(a);
    else if (isThisWeek(d)) week.push(a);
    else older.push(a);
  }
  return { today, week, older };
};

type TabType = "all" | "unread" | "critical";

const AlertsPage: React.FC = () => {
  const [tab, setTab] = useState<TabType>("all");
  const { data: alerts, isLoading } = useAlerts();
  const markRead = useMarkAlertRead();
  const markAllRead = useMarkAllAlertsRead();

  const filtered = (alerts ?? []).filter((a) => {
    if (tab === "unread") return !a.isRead;
    if (tab === "critical")
      return a.severity === "CRITICAL" || a.severity === "HIGH";
    return true;
  });

  const unreadCount = (alerts ?? []).filter((a) => !a.isRead).length;
  const groups = groupAlerts(filtered);

  const tabs: { id: TabType; label: string; count?: number }[] = [
    { id: "all", label: "Toutes", count: alerts?.length },
    { id: "unread", label: "Non lues", count: unreadCount },
    { id: "critical", label: "Critiques" },
  ];

  const Section: React.FC<{ title: string; items: Alert[] }> = ({
    title,
    items,
  }) => {
    if (items.length === 0) return null;
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <span className="text-text-muted text-xs font-medium uppercase tracking-widest">
            {title}
          </span>
          <div className="flex-1 h-px bg-white/[0.06]" />
        </div>
        {items.map((a) => (
          <AlertRow
            key={a.id}
            alert={a}
            onRead={(id) => markRead.mutate(id)}
            loading={markRead.isPending}
          />
        ))}
      </div>
    );
  };

  return (
    <Layout title="Alertes">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="page-title">Alertes</h1>
            <p className="page-subtitle">
              Notifications et alertes de trésorerie
            </p>
          </div>
          {unreadCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              leftIcon={<CheckCheck size={15} />}
              isLoading={markAllRead.isPending}
              onClick={() => markAllRead.mutate()}
            >
              Tout marquer lu
            </Button>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-1 bg-white/[0.03] border border-white/[0.06] rounded-xl p-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`
                flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-sm font-medium
                transition-all duration-150
                ${
                  tab === t.id
                    ? "bg-primary/15 text-primary"
                    : "text-text-secondary hover:text-white"
                }
              `}
            >
              {t.label}
              {t.count !== undefined && t.count > 0 && (
                <span
                  className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                    tab === t.id
                      ? "bg-primary text-white"
                      : "bg-white/10 text-text-secondary"
                  }`}
                >
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        {isLoading ? (
          <Loader text="Chargement des alertes…" />
        ) : filtered.length === 0 ? (
          <EmptyState
            title="Aucune alerte"
            description="Tout va bien dans votre trésorerie !"
            icon={<Bell size={28} />}
          />
        ) : (
          <div className="space-y-6">
            <Section title="Aujourd'hui" items={groups.today} />
            <Section title="Cette semaine" items={groups.week} />
            {groups.older.length > 0 && (
              <div>
                <div className="flex items-center gap-3 mb-3">
                  <span className="text-text-muted text-xs font-medium uppercase tracking-widest">
                    Plus ancien
                  </span>
                  <div className="flex-1 h-px bg-white/[0.06]" />
                </div>
                <Card
                  padding="none"
                  className="overflow-hidden divide-y divide-white/[0.04]"
                >
                  {groups.older.map((a) => (
                    <AlertRow
                      key={a.id}
                      alert={a}
                      onRead={(id) => markRead.mutate(id)}
                      loading={markRead.isPending}
                    />
                  ))}
                </Card>
              </div>
            )}
          </div>
        )}
      </div>
    </Layout>
  );
};

export default AlertsPage;
