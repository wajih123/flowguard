import apiClient from "./client";

export interface CashGoal {
  id: string;
  targetAmount: number;
  label: string;
  currentBalance: number;
  progressPercent: number;
  estimatedDaysToReach: number;
  estimatedDate: string | null;
}

export interface UpsertGoalPayload {
  targetAmount: number;
  label?: string;
}

export const cashGoalApi = {
  get: () => apiClient.get<CashGoal>("/api/cash-goal").then((r) => r.data),
  upsert: (payload: UpsertGoalPayload) =>
    apiClient.put<CashGoal>("/api/cash-goal", payload).then((r) => r.data),
  delete: () => apiClient.delete("/api/cash-goal"),
};
