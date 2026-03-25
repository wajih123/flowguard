import apiClient from "./client";
import type { DashboardData, Transaction } from "@/types";

export interface AccountBreakdown {
  id: string;
  bankName: string;
  ibanMasked: string;
  balance: number;
  syncStatus: string;
}

export interface UpcomingDebit {
  label: string;
  amount: number;
  expectedDate: string;
}

export interface OverdraftRiskSummary {
  level: "NONE" | "MEDIUM" | "HIGH";
  projectedBalance: number;
  horizonDate: string;
}

export interface DashboardSummary {
  totalBalance: number;
  accounts: AccountBreakdown[];
  healthScore: number;
  unreadAlerts: number;
  lastMonthIncome: number;
  lastMonthSpend: number;
  lastMonthSavings: number;
  monthlySubscriptionsCost: number;
  upcomingDebits: UpcomingDebit[];
  overdraftRisk: OverdraftRiskSummary;
}

export interface SpendingCategory {
  category: string;
  amount: number;
}

export const dashboardApi = {
  getSummary: () =>
    apiClient.get<DashboardData>("/api/dashboard/summary").then((r) => r.data),

  getEnrichedSummary: () =>
    apiClient
      .get<DashboardSummary>("/api/dashboard/summary")
      .then((r) => r.data),

  getTransactions: (limit = 5, accountId?: string) =>
    apiClient
      .get<Transaction[]>("/api/dashboard/transactions", {
        params: { limit, ...(accountId ? { accountId } : {}) },
      })
      .then((r) => r.data),

  getSpendingByCategory: (months = 1) =>
    apiClient
      .get<SpendingCategory[]>("/api/dashboard/spending-by-category", {
        params: { months },
      })
      .then((r) => r.data),

  sync: () => apiClient.post("/api/dashboard/sync"),
};
