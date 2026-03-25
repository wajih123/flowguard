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

export interface Prediction {
  date: string;
  predictedBalance: number;
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
  predictions: Prediction[];
}

export interface SpendingCategory {
  category: string;
  amount: number;
}

// Transform backend DashboardSummary to frontend DashboardData
const transformSummaryToData = (summary: DashboardSummary): DashboardData => {
  const totalAccounts = summary.accounts.length;
  const primaryAccount =
    summary.accounts.length > 0
      ? summary.accounts[0]
      : {
          id: "",
          bankName: "Comptes actifs",
          ibanMasked: "",
          balance: summary.totalBalance,
          syncStatus: "OK",
        };

  const predictedBalance30d =
    summary.predictions.length > 0
      ? summary.predictions[summary.predictions.length - 1].predictedBalance
      : summary.totalBalance;

  return {
    account: {
      id: primaryAccount.id,
      bankName:
        totalAccounts > 1 ? "Tous comptes actifs" : primaryAccount.bankName,
      ibanMasked:
        totalAccounts > 1
          ? `${totalAccounts} comptes`
          : primaryAccount.ibanMasked,
      currentBalance: summary.totalBalance,
      currency: "EUR",
      lastSyncAt: new Date().toISOString(),
      syncStatus:
        (primaryAccount.syncStatus as "PENDING" | "SYNCING" | "OK" | "ERROR") ||
        "OK",
    },
    currentBalance: summary.totalBalance,
    predictedBalance30d,
    balanceTrend:
      summary.predictions.length > 1
        ? summary.predictions[summary.predictions.length - 1].predictedBalance -
          summary.predictions[0].predictedBalance
        : 0,
    healthScore: summary.healthScore,
    healthLabel:
      summary.healthScore >= 70 ? "Bonne santé" : "Attention requise",
    reserveAvailable: summary.overdraftRisk.level === "NONE",
    reserveMaxAmount: Math.max(...summary.accounts.map((a) => a.balance)),
    hasHighAlert: summary.overdraftRisk.level === "HIGH",
    highAlertMessage:
      summary.overdraftRisk.level === "HIGH"
        ? `Alerte : solde prévu à ${summary.overdraftRisk.projectedBalance} € le ${summary.overdraftRisk.horizonDate}`
        : undefined,
    highAlertAmount: summary.overdraftRisk.projectedBalance,
    highAlertDate: summary.overdraftRisk.horizonDate,
    accountCount: totalAccounts,
  };
};

export const dashboardApi = {
  getSummary: async (): Promise<DashboardData> => {
    const summary = await apiClient
      .get<DashboardSummary>("/api/dashboard/summary")
      .then((r) => r.data);
    return transformSummaryToData(summary);
  },

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
