import apiClient from "./client";
import type { SpendingAnalysis } from "@/domain/SpendingAnalysis";

export const spendingApi = {
  get: (accountId: string, params?: { from?: string; to?: string }) =>
    apiClient
      .get<SpendingAnalysis>(`/api/accounts/${accountId}/spending-analysis`, {
        params,
      })
      .then((r) => r.data),
};
