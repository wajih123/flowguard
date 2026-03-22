import React, { useMemo, useState } from "react";
import {
  CalendarDays,
  RefreshCw,
  TrendingDown,
  TrendingUp,
  Clock,
  AlertTriangle,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery } from "@tanstack/react-query";
import { cashCalendarApi } from "@/api/cashCalendar";
import type { CashCalendarEvent } from "@/api/cashCalendar";
import { format, parseISO, isToday, isTomorrow } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    signDisplay: "exceptZero",
  }).format(n);

const fmtDate = (d: string) => {
  const date = parseISO(d);
  if (isToday(date)) return "Aujourd'hui";
  if (isTomorrow(date)) return "Demain";
  return format(date, "EEE d MMM", { locale: fr });
};

const EVENT_META: Record<
  CashCalendarEvent["type"],
  { color: string; bg: string; icon: React.ReactNode; label: string }
> = {
  INVOICE_DUE: {
    color: "text-success",
    bg: "bg-success/10 border-success/20",
    icon: <TrendingUp size={13} />,
    label: "Facture attendue",
  },
  INVOICE_OVERDUE: {
    color: "text-danger",
    bg: "bg-danger/10 border-danger/20",
    icon: <AlertTriangle size={13} />,
    label: "Facture en retard",
  },
  RECURRING_CHARGE: {
    color: "text-danger",
    bg: "bg-white/[0.03] border-white/[0.07]",
    icon: <TrendingDown size={13} />,
    label: "Charge récurrente",
  },
  RECURRING_INCOME: {
    color: "text-success",
    bg: "bg-white/[0.03] border-white/[0.07]",
    icon: <TrendingUp size={13} />,
    label: "Revenu récurrent",
  },
};

const EventRow: React.FC<{ event: CashCalendarEvent }> = ({ event }) => {
  const meta = EVENT_META[event.type];
  return (
    <div
      className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border ${meta.bg} transition`}
    >
      <span className={`flex-shrink-0 ${meta.color}`}>{meta.icon}</span>
      <div className="flex-1 min-w-0">
        <p className="text-white text-sm font-medium truncate">{event.label}</p>
        {event.clientName && (
          <p className="text-text-muted text-xs truncate">{event.clientName}</p>
        )}
      </div>
      <div className="text-right shrink-0">
        <p className={`font-numeric font-semibold text-sm ${meta.color}`}>
          {fmt(event.amount)}
        </p>
        {event.status === "PREDICTED" && (
          <p className="text-text-muted text-xs">Estimé</p>
        )}
      </div>
    </div>
  );
};

const CashCalendarPage: React.FC = () => {
  const [filter, setFilter] = useState<"ALL" | "IN" | "OUT">("ALL");

  const {
    data: events,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["cash-calendar"],
    queryFn: cashCalendarApi.list,
    staleTime: 10 * 60 * 1000,
  });

  // Group by date string
  const grouped = useMemo(() => {
    if (!events) return {};
    const filtered = events.filter((e) => {
      if (filter === "IN") return e.amount > 0;
      if (filter === "OUT") return e.amount < 0;
      return true;
    });
    return filtered.reduce(
      (acc, event) => {
        if (!acc[event.date]) acc[event.date] = [];
        acc[event.date].push(event);
        return acc;
      },
      {} as Record<string, CashCalendarEvent[]>,
    );
  }, [events, filter]);

  const totalIn =
    events?.filter((e) => e.amount > 0).reduce((s, e) => s + e.amount, 0) ?? 0;
  const totalOut =
    events?.filter((e) => e.amount < 0).reduce((s, e) => s + e.amount, 0) ?? 0;
  const overdueCount =
    events?.filter((e) => e.type === "INVOICE_OVERDUE").length ?? 0;

  return (
    <Layout title="Calendrier de trésorerie">
      <div className="max-w-3xl mx-auto space-y-5 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <h1
              className="text-2xl font-bold text-white"
              style={{ fontFamily: "var(--font-display)" }}
            >
              Calendrier de trésorerie
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Factures attendues · charges récurrentes · 60 jours à venir
            </p>
          </div>
          <button
            onClick={() => refetch()}
            className="p-2 rounded-lg text-text-muted hover:text-white hover:bg-white/[0.05] transition"
            title="Actualiser"
          >
            <RefreshCw size={16} />
          </button>
        </div>

        {/* KPI row */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">Encaissements prévus</p>
            <p className="font-numeric text-success text-xl font-bold">
              {fmt(totalIn)}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">
              60 prochains jours
            </p>
          </Card>
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">Décaissements prévus</p>
            <p className="font-numeric text-danger text-xl font-bold">
              {fmt(totalOut)}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">
              Charges + remboursements
            </p>
          </Card>
          <Card padding="md">
            <p className="text-text-muted text-xs mb-1">Factures en retard</p>
            <p
              className={`font-numeric text-xl font-bold ${overdueCount > 0 ? "text-danger" : "text-success"}`}
            >
              {overdueCount}
            </p>
            <p className="text-text-muted text-2xs mt-0.5">À relancer</p>
          </Card>
        </div>

        {/* Filter tabs */}
        <div className="flex gap-2">
          {(["ALL", "IN", "OUT"] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                filter === f
                  ? "bg-primary text-white"
                  : "bg-white/[0.05] text-text-muted hover:text-white"
              }`}
            >
              {f === "ALL" ? "Tout" : f === "IN" ? "Entrées" : "Sorties"}
            </button>
          ))}
        </div>

        {/* Timeline */}
        <Card padding="none">
          <CardHeader
            title="Événements à venir"
            helpTooltip={
              <HelpTooltip text="Factures émises non payées et charges récurrentes détectées, projetées sur 60 jours." />
            }
          />
          <div className="px-4 pb-4">
            {isLoading ? (
              <Loader text="Chargement du calendrier…" />
            ) : Object.keys(grouped).length === 0 ? (
              <div className="flex flex-col items-center py-10 gap-3 text-text-muted">
                <CalendarDays size={32} className="opacity-30" />
                <p className="text-sm">Aucun événement prévu</p>
                <p className="text-xs">
                  Créez des factures ou connectez un compte bancaire
                </p>
              </div>
            ) : (
              <div className="space-y-5">
                {Object.entries(grouped).map(([date, dayEvents]) => (
                  <div key={date}>
                    <div className="flex items-center gap-2 mb-2">
                      <Clock size={12} className="text-text-muted" />
                      <p className="text-text-muted text-xs font-semibold uppercase tracking-wide">
                        {fmtDate(date)}
                      </p>
                      <div className="flex-1 h-px bg-white/[0.05]" />
                    </div>
                    <div className="space-y-2">
                      {dayEvents.map((e, i) => (
                        <EventRow key={`${date}-${i}`} event={e} />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Card>
      </div>
    </Layout>
  );
};

export default CashCalendarPage;
