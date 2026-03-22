import apiClient from "./client";

export interface SubscriptionSummary {
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
};
