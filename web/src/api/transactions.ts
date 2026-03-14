import apiClient from "./client";
import type { Transaction } from "@/domain/Transaction";

export const transactionsApi = {
  list: (
    accountId: string,
    params?: { from?: string; to?: string; category?: string },
  ) =>
    apiClient
      .get<Transaction[]>(`/api/accounts/${accountId}/transactions`, { params })
      .then((r) => r.data),

  recurring: (accountId: string) =>
    apiClient
      .get<Transaction[]>(`/api/accounts/${accountId}/transactions/recurring`)
      .then((r) => r.data),
};
