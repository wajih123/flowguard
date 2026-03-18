import apiClient from "./client";

export interface BudgetCategory {
  id: string;
  periodYear: number;
  periodMonth: number;
  category: string;
  budgetedAmount: number;
}

export interface BudgetVsActual {
  year: number;
  month: number;
  lines: BudgetVsActualLine[];
  totalBudgeted: number;
  totalActual: number;
  netVariance: number;
}

export interface BudgetVsActualLine {
  category: string;
  budgeted: number;
  actual: number;
  variance: number;
  status: "OVER_BUDGET" | "UNDER_BUDGET" | "ON_TRACK" | "UNBUDGETED";
}

export const budgetApi = {
  get: (year: number, month: number) =>
    apiClient
      .get<BudgetCategory[]>(`/api/budget/${year}/${month}`)
      .then((r) => r.data),

  upsert: (year: number, month: number, category: string, amount: number) =>
    apiClient
      .put<BudgetCategory>(`/api/budget/${year}/${month}/${category}`, amount)
      .then((r) => r.data),

  deleteLine: (budgetId: string) =>
    apiClient.delete(`/api/budget/line/${budgetId}`),

  vsActual: (year: number, month: number) =>
    apiClient
      .get<BudgetVsActual>(`/api/budget/vs-actual/${year}/${month}`)
      .then((r) => r.data),
};
