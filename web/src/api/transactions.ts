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

  /**
   * Import a bank statement in any supported format (PDF, OFX, QIF, MT940,
   * CFONB, XLSX, XLS, CSV).  The format is auto-detected server-side from the
   * filename extension + file content.
   * Returns how many rows were imported, skipped (duplicates/errors), and the
   * detected format name.
   */
  importStatement: (accountId: string, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient
      .post<{
        imported: number;
        skipped: number;
        format: string;
      }>(`/api/accounts/${accountId}/transactions/import`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => r.data);
  },

  /** @deprecated use importStatement — kept for backward compatibility */
  importCsv: (accountId: string, file: File) =>
    transactionsApi.importStatement(accountId, file),
};
