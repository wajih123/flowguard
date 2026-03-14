import React from "react";
import { Bell, CheckCheck } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { SkeletonRow } from "@/components/ui/SkeletonCard";
import { useAlerts, useMarkAllAlertsRead } from "@/hooks/useAlerts";
import type { Alert } from "@/domain/Alert";
import { formatDateShort } from "@/utils/format";

const severityConfig: Record<
  string,
  { dot: string; text: string; bg: string }
> = {
  HIGH: {
    dot: "bg-danger",
    text: "text-danger",
    bg: "bg-danger/[0.06] border-danger/15",
  },
  MEDIUM: {
    dot: "bg-warning",
    text: "text-warning",
    bg: "bg-warning/[0.06] border-warning/15",
  },
  LOW: {
    dot: "bg-info",
    text: "text-info",
    bg: "bg-info/[0.06] border-info/15",
  },
  INFO: {
    dot: "bg-primary",
    text: "text-primary",
    bg: "bg-primary/[0.06] border-primary/15",
  },
};

interface AlertsListProps {
  limit?: number;
}

export const AlertsList: React.FC<AlertsListProps> = ({ limit = 5 }) => {
  const { data: alerts, isLoading } = useAlerts();
  const markAll = useMarkAllAlertsRead();

  const displayed = (alerts ?? []).slice(0, limit);
  const unreadCount = (alerts ?? []).filter((a) => !a.isRead).length;

  return (
    <Card padding="none">
      <div className="px-5 pt-5 pb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Bell size={16} className="text-text-secondary" />
          <p
            className="text-white font-semibold text-sm"
            style={{ fontFamily: "var(--font-display)" }}
          >
            Alertes
          </p>
          {unreadCount > 0 && (
            <span className="min-w-[20px] h-5 flex items-center justify-center text-xs bg-danger text-white rounded-full px-1.5 font-medium">
              {unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <Button
            variant="ghost"
            size="xs"
            leftIcon={<CheckCheck size={12} />}
            onClick={() => markAll.mutate()}
            isLoading={markAll.isPending}
          >
            Tout lire
          </Button>
        )}
      </div>

      <div className="divide-y divide-white/[0.04]">
        {isLoading ? (
          <>
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
          </>
        ) : !displayed.length ? (
          <div className="py-8 text-center">
            <Bell size={24} className="text-text-muted mx-auto mb-2" />
            <p className="text-text-secondary text-sm">Aucune alerte</p>
          </div>
        ) : (
          displayed.map((alert: Alert) => {
            const cfg = severityConfig[alert.severity] ?? severityConfig.INFO;
            return (
              <div
                key={alert.id}
                className={`px-5 py-3.5 border-l-4 ${cfg.bg} ${
                  !alert.isRead ? "border-l-current" : "border-l-transparent"
                }`}
                style={{
                  borderLeftColor: !alert.isRead
                    ? cfg.dot.replace("bg-", "")
                    : "transparent",
                }}
              >
                <div className="flex items-start gap-2">
                  <span
                    className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${cfg.dot}`}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-white text-sm leading-snug">
                      {alert.message}
                    </p>
                    {alert.createdAt && (
                      <p className="text-text-muted text-xs mt-0.5">
                        {formatDateShort(alert.createdAt)}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </Card>
  );
};
