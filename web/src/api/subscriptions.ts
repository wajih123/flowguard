import apiClient from "./client";

export interface SubscriptionSummary {
  id: string;
  label: string;
  category: string;
  monthlyAmount: number;
  lastUsedDate: string;
  monthsSinceLastUse: number;
  isStale: boolean;
  occurrencesLast12Months: number;
}

export const subscriptionsApi = {
  list: () =>
    apiClient
      .get<SubscriptionSummary[]>("/api/subscriptions")
      .then((r) => r.data),

  detect: () =>
    apiClient
      .post<SubscriptionSummary[]>("/api/subscriptions/detect")
      .then((r) => r.data),

  remove: (id: string) => apiClient.delete(`/api/subscriptions/${id}`),
};
