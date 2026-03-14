import React, { useState } from "react";
import { Bell, Filter } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Select } from "@/components/ui/Input";
import { Badge, severityBadge } from "@/components/ui/Badge";
import { Loader } from "@/components/ui/Loader";
import { EmptyState } from "@/components/ui/EmptyState";
import { adminApi } from "@/api/admin";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const ALERT_TYPE_LABELS: Record<string, string> = {
  CASH_SHORTAGE: "Manque de liquidités",
  UNUSUAL_SPEND: "Dépense inhabituelle",
  PAYMENT_DUE: "Paiement à venir",
  POSITIVE_TREND: "Tendance positive",
};

const AdminAlertsPage: React.FC = () => {
  const [severityFilter, setSeverityFilter] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "alerts", severityFilter],
    queryFn: () =>
      adminApi.listAlerts({ severity: severityFilter || undefined }),
    staleTime: 60_000,
    refetchInterval: 2 * 60 * 1000, // refresh every 2 min
  });

  const alerts = data?.content ?? [];

  return (
    <AdminLayout title="Alertes système">
      <div className="max-w-5xl mx-auto space-y-6 animate-fade-in">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">Alertes</h1>
            <p className="text-text-secondary text-sm mt-1">
              {data?.totalElements ?? 0} alertes au total
            </p>
          </div>
          <div className="w-48">
            <Select
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value)}
              options={[
                { value: "", label: "Toutes sévérités" },
                { value: "LOW", label: "Faible" },
                { value: "MEDIUM", label: "Moyen" },
                { value: "HIGH", label: "Élevé" },
                { value: "CRITICAL", label: "Critique" },
              ]}
            />
          </div>
        </div>

        <Card padding="none">
          {isLoading ? (
            <Loader />
          ) : alerts.length === 0 ? (
            <EmptyState title="Aucune alerte" icon={<Bell size={28} />} />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Sévérité</th>
                    <th>Type</th>
                    <th>Message</th>
                    <th>Déficit prévu</th>
                    <th>Date</th>
                    <th>Lue</th>
                  </tr>
                </thead>
                <tbody>
                  {alerts.map((a) => (
                    <tr
                      key={a.id}
                      className={
                        a.severity === "CRITICAL" && !a.isRead
                          ? "bg-danger/[0.03]"
                          : ""
                      }
                    >
                      <td>
                        <Badge variant={severityBadge(a.severity)} dot>
                          {a.severity}
                        </Badge>
                      </td>
                      <td className="text-text-secondary text-xs">
                        {ALERT_TYPE_LABELS[a.type] ?? a.type}
                      </td>
                      <td className="max-w-xs">
                        <p className="text-sm truncate">{a.message}</p>
                      </td>
                      <td className="text-danger text-sm">
                        {a.projectedDeficit
                          ? new Intl.NumberFormat("fr-FR", {
                              style: "currency",
                              currency: "EUR",
                            }).format(a.projectedDeficit)
                          : "—"}
                      </td>
                      <td className="text-text-secondary text-xs whitespace-nowrap">
                        {format(parseISO(a.createdAt), "d MMM yyyy HH:mm", {
                          locale: fr,
                        })}
                      </td>
                      <td>
                        {a.isRead ? (
                          <Badge variant="muted">Lu</Badge>
                        ) : (
                          <Badge variant="warning">Non lu</Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </AdminLayout>
  );
};

export default AdminAlertsPage;
