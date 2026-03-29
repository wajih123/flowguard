import apiClient from "./client";

export type GoalType =
  | "EMERGENCY_FUND"
  | "VACATION"
  | "EQUIPMENT"
  | "REAL_ESTATE"
  | "EDUCATION"
  | "RETIREMENT"
  | "PROJECT"
  | "OTHER";

export interface SavingsGoal {
  id: string;
  goalType: GoalType;
  goalTypeLabel: string;
  goalTypeEmoji: string;
  label: string;
  targetAmount: number;
  currentBalance: number;
  progressPercent: number;
  targetDate: string | null;
  monthlyContribution: number | null;
  recommendedMonthly: number;
  estimatedDaysToReach: number;
  estimatedDate: string | null;
  coachTip: string | null;
}

export interface CreateGoalPayload {
  goalType: GoalType;
  label: string;
  targetAmount: number;
  targetDate?: string;
  monthlyContribution?: number;
}

export interface UpdateGoalPayload {
  goalType?: GoalType;
  label?: string;
  targetAmount?: number;
  targetDate?: string | null;
  monthlyContribution?: number | null;
}

export const savingsGoalsApi = {
  list: () =>
    apiClient.get<SavingsGoal[]>("/api/savings-goals").then((r) => r.data),
  get: (id: string) =>
    apiClient.get<SavingsGoal>(`/api/savings-goals/${id}`).then((r) => r.data),
  create: (payload: CreateGoalPayload) =>
    apiClient
      .post<SavingsGoal>("/api/savings-goals", payload)
      .then((r) => r.data),
  update: (id: string, payload: UpdateGoalPayload) =>
    apiClient
      .put<SavingsGoal>(`/api/savings-goals/${id}`, payload)
      .then((r) => r.data),
  delete: (id: string) => apiClient.delete(`/api/savings-goals/${id}`),
};
