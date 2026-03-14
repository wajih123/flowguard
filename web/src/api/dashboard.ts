import apiClient from "./client";
import type { DashboardData, Transaction } from "@/types";

export const dashboardApi = {
  getSummary: () =>
    apiClient.get<DashboardData>("/api/dashboard/summary").then((r) => r.data),

  getTransactions: (limit = 5) =>
    apiClient
      .get<Transaction[]>("/api/dashboard/transactions", {
        params: { limit },
      })
      .then((r) => r.data),

  sync: () => apiClient.post("/api/dashboard/sync"),
};
