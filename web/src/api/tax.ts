import apiClient from "./client";

export type TaxType = "TVA" | "URSSAF" | "IS" | "IR" | "CFE";

export interface TaxEstimate {
  id: string;
  taxType: TaxType;
  periodLabel: string;
  estimatedAmount: number;
  dueDate: string;
  paidAt: string | null;
  daysUntilDue: number;
  isPaid: boolean;
}

export interface TaxProvision {
  incomeType: string;
  avgMonthlyIncome: number;
  cotisationRate: number;
  cotisationMonthly: number;
  incomeTaxRate: number;
  incomeTaxMonthly: number;
  totalMonthlyProvision: number;
  urssafEstimate: number;
  nextUrssafDate: string;
  tip: string;
}

export const taxApi = {
  getAll: () => apiClient.get<TaxEstimate[]>("/api/tax").then((r) => r.data),

  getUpcoming: () =>
    apiClient.get<TaxEstimate[]>("/api/tax/upcoming").then((r) => r.data),

  regenerate: () => apiClient.post("/api/tax/regenerate"),

  markPaid: (id: string) =>
    apiClient.post<TaxEstimate>(`/api/tax/${id}/mark-paid`).then((r) => r.data),

  getProvision: () =>
    apiClient.get<TaxProvision>("/api/tax/provision").then((r) => r.data),
};
