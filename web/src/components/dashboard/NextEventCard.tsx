import React from "react";
import { CalendarClock, CheckCircle2 } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { AmountDisplay } from "@/components/ui/AmountDisplay";
import { SkeletonCard } from "@/components/ui/SkeletonCard";
import type { Prediction } from "@/types";
import { formatDateShort } from "@/utils/format";
import { differenceInDays, parseISO } from "date-fns";

interface NextEventCardProps {
  prediction?: Prediction;
  isLoading: boolean;
}

export const NextEventCard: React.FC<NextEventCardProps> = ({
  prediction,
  isLoading,
}) => {
  if (isLoading) return <SkeletonCard lines={3} />;

  const nextCritical = prediction?.criticalPoints[0];

  if (!nextCritical) {
    return (
      <Card padding="md" className="flex flex-col">
        <p className="text-text-secondary text-xs uppercase tracking-widest font-medium mb-3">
          Prochain événement
        </p>
        <div className="flex-1 flex flex-col items-center justify-center py-6 gap-2">
          <CheckCircle2 size={28} className="text-success" />
          <p className="text-text-secondary text-sm text-center">
            Aucun événement critique prévu
          </p>
        </div>
      </Card>
    );
  }

  const daysUntil = differenceInDays(parseISO(nextCritical.date), new Date());
  const isExpense = nextCritical.amount < 0;

  const urgencyColor =
    daysUntil <= 3
      ? "text-danger"
      : daysUntil <= 7
        ? "text-warning"
        : "text-text-secondary";

  const urgencyBg =
    daysUntil <= 3
      ? "bg-danger/[0.08] border-danger/20"
      : daysUntil <= 7
        ? "bg-warning/[0.08] border-warning/15"
        : "bg-white/[0.04] border-white/[0.08]";

  return (
    <Card padding="md" className="flex flex-col">
      <p className="text-text-secondary text-xs uppercase tracking-widest font-medium mb-3">
        Prochain événement
      </p>

      <div className={`flex-1 rounded-xl border p-3 ${urgencyBg}`}>
        <div className="flex items-start gap-2 mb-3">
          <CalendarClock
            size={18}
            className={`flex-shrink-0 mt-0.5 ${urgencyColor}`}
          />
          <div className="flex-1 min-w-0">
            <p
              className="text-white text-sm font-medium leading-tight truncate"
              style={{ fontFamily: "var(--font-display)" }}
            >
              {nextCritical.label}
            </p>
            <p className={`text-xs mt-0.5 ${urgencyColor}`}>
              {formatDateShort(nextCritical.date)}
              {daysUntil === 0
                ? " — Aujourd'hui"
                : daysUntil === 1
                  ? " — Demain"
                  : ` — Dans ${daysUntil} jours`}
            </p>
          </div>
        </div>

        <AmountDisplay
          amount={nextCritical.amount}
          size="lg"
          showSign={!isExpense}
          colorOverride={isExpense ? "danger" : "success"}
        />
      </div>

      {prediction && prediction.criticalPoints.length > 1 && (
        <p className="text-text-muted text-xs mt-2">
          +{prediction.criticalPoints.length - 1} autre
          {prediction.criticalPoints.length > 2 ? "s" : ""} événement
          {prediction.criticalPoints.length > 2 ? "s" : ""} à venir
        </p>
      )}
    </Card>
  );
};
