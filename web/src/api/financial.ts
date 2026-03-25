import apiClient from "./client";

export interface IncomeProfile {
  incomeType: "SALARIED" | "FREELANCE" | "MIXED" | "UNKNOWN";
  avgMonthlyIncome: number;
  totalIncomeIn6Months: number;
  mainSources: string[];
  isFreelance: boolean;
  tip: string;
}

export interface OverdraftRiskDetail {
  riskLevel: "NONE" | "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN";
  currentBalance: number;
  projectedBalance: number;
  avgDailySpend: number;
  scheduledDebitsIn72h: number;
  horizonDate: string;
  recommendation: string;
}

export interface SweepSuggestion {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  suggestedAmount: number;
  reason: string;
  predictedDeficitDate: string | null;
  status: "PENDING" | "ACCEPTED" | "DISMISSED" | "EXPIRED";
}

export const incomeProfileApi = {
  get: () =>
    apiClient.get<IncomeProfile>("/api/income-profile").then((r) => r.data),
};

export const overdraftApi = {
  risk: () =>
    apiClient
      .get<OverdraftRiskDetail>("/api/overdraft/risk")
      .then((r) => r.data),
};

export const sweepApi = {
  list: () =>
    apiClient.get<SweepSuggestion[]>("/api/sweep").then((r) => r.data),

  accept: (id: string) => apiClient.put(`/api/sweep/${id}/accept`),

  dismiss: (id: string) => apiClient.put(`/api/sweep/${id}/dismiss`),
};
