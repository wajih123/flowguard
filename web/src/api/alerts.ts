import apiClient from "./client";
import type {
  Alert,
  AlertThreshold,
  AlertThresholdRequest,
} from "@/domain/Alert";

export const alertsApi = {
  list: (unreadOnly?: boolean) =>
    apiClient
      .get<
        Alert[]
      >("/api/alerts", { params: unreadOnly ? { unreadOnly: true } : {} })
      .then((r) => r.data),

  unreadCount: () =>
    apiClient
      .get<{ count: number }>("/api/alerts/unread-count")
      .then((r) => r.data.count),

  markRead: (alertId: string) => apiClient.put(`/api/alerts/${alertId}/read`),

  markAllRead: () => apiClient.put("/api/alerts/read-all"),

  getThresholds: () =>
    apiClient
      .get<AlertThreshold[]>("/api/alert-thresholds")
      .then((r) => r.data),

  upsertThreshold: (data: AlertThresholdRequest) =>
    apiClient
      .put<AlertThreshold>("/api/alert-thresholds", data)
      .then((r) => r.data),
};
