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

  /** Import transactions from a CSV file (multipart/form-data).
   *  The CSV must have columns: date, label, amount, type (DEBIT|CREDIT).
   *  Returns the number of rows successfully imported. */
  importCsv: (accountId: string, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient
      .post<{
        imported: number;
        skipped: number;
      }>(`/api/accounts/${accountId}/transactions/import-csv`, formData, { headers: { "Content-Type": "multipart/form-data" } })
      .then((r) => r.data);
  },
};
