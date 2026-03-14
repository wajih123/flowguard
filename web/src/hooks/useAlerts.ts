import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { alertsApi } from "@/api/alerts";
import { useAlertStore } from "@/store/alertStore";
import { useEffect } from "react";
import type { Alert } from "@/domain/Alert";

const mockAlerts: Alert[] = [
  {
    id: "alert-001",
    severity: "HIGH",
    type: "CASH_SHORTAGE",
    message:
      "Votre solde prévu sera de -230 € le 18 mars. URSSAF de 850 € attendue.",
    projectedDeficit: -230,
    triggerDate: "2026-03-18",
    isRead: false,
    createdAt: new Date().toISOString(),
  },
  {
    id: "alert-002",
    severity: "MEDIUM",
    type: "PAYMENT_DUE",
    message: "Prélèvement de 1 200 € prévu le 25 mars.",
    isRead: false,
    createdAt: new Date().toISOString(),
  },
];

const isMock = import.meta.env.VITE_MOCK_DATA === "true";

export const useAlerts = (unreadOnly?: boolean) => {
  const { setUnreadCount } = useAlertStore();
  const query = useQuery<Alert[]>({
    queryKey: ["alerts", unreadOnly],
    queryFn: isMock
      ? () =>
          new Promise<Alert[]>((resolve) =>
            setTimeout(
              () =>
                resolve(
                  unreadOnly ? mockAlerts.filter((a) => !a.isRead) : mockAlerts,
                ),
              400,
            ),
          )
      : () => alertsApi.list(unreadOnly),
    staleTime: 60_000,
  });

  useEffect(() => {
    if (query.data) {
      const count = query.data.filter((a) => !a.isRead).length;
      setUnreadCount(count);
    }
  }, [query.data, setUnreadCount]);

  return query;
};

export const useMarkAlertRead = () => {
  const qc = useQueryClient();
  const { decrementUnread } = useAlertStore();
  return useMutation({
    mutationFn: alertsApi.markRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alerts"] });
      decrementUnread();
    },
  });
};

export const useMarkAllAlertsRead = () => {
  const qc = useQueryClient();
  const { resetUnread } = useAlertStore();
  return useMutation({
    mutationFn: alertsApi.markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alerts"] });
      resetUnread();
    },
  });
};
