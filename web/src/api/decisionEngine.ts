import apiClient from "./client";

// ── Types ──────────────────────────────────────────────────────────────────

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type ActionType =
  | "DELAY_SUPPLIER"
  | "SEND_REMINDERS"
  | "REDUCE_SPEND"
  | "REQUEST_CREDIT"
  | "ACCELERATE_RECEIVABLES";
export type ActionStatus = "PENDING" | "APPLIED" | "DISMISSED";
export type ScenarioType = "HIRE_EMPLOYEE" | "REVENUE_DROP" | "PAYMENT_DELAY";

export interface CashDriver {
  id: string;
  type: string;
  label: string;
  amount: number;
  impactDays: number;
  dueDate: string | null;
  referenceId: string | null;
  referenceType: string | null;
  rank: number;
}

export interface CashAction {
  id: string;
  actionType: ActionType;
  description: string;
  estimatedImpact: number;
  horizonDays: number;
  confidence: number;
  status: ActionStatus;
}

export interface DecisionSummary {
  snapshotId: string;
  computedAt: string;
  riskLevel: RiskLevel;
  runwayDays: number;
  currentBalance: number;
  minProjectedBalance: number;
  minProjectedDate: string | null;
  deficitPredicted: boolean;
  volatilityScore: number;
  drivers: CashDriver[];
  actions: CashAction[];
  forecast?: {
    confidenceScore: number;
    horizonDays: number;
    dailyData: Array<{
      date: string;
      balance: number;
      p25: number;
      p75: number;
    }>;
  };
}

export interface SimulateRequest {
  scenarioType: ScenarioType;
  amount?: number;
  percentage?: number;
  daysDelay?: number;
}

export interface SimulateResult {
  scenarioType: ScenarioType;
  baseBalance: number;
  projectedBalance: number;
  balanceDelta: number;
  baseRunwayDays: number;
  projectedRunwayDays: number;
  explanation: string;
}

export interface WeeklyBrief {
  id: string;
  briefText: string;
  riskLevel: RiskLevel;
  runwayDays: number;
  generatedAt: string;
  generationMode: "CRON" | "ON_DEMAND";
}

// ── API ────────────────────────────────────────────────────────────────────

export const decisionEngineApi = {
  /** Full summary: risk, drivers, actions (10-min cache on backend) */
  getSummary: () =>
    apiClient
      .get<DecisionSummary>("/decision-engine/summary")
      .then((r) => r.data),

  /** Force recompute, bypasses cache */
  refresh: () =>
    apiClient
      .post<DecisionSummary>("/decision-engine/refresh")
      .then((r) => r.data),

  /** Top cash flow drivers only */
  getDrivers: () =>
    apiClient.get<CashDriver[]>("/decision-engine/drivers").then((r) => r.data),

  /** Pending recommendations */
  getActions: () =>
    apiClient.get<CashAction[]>("/decision-engine/actions").then((r) => r.data),

  /** Scenario simulation */
  simulate: (req: SimulateRequest) =>
    apiClient
      .post<SimulateResult>("/decision-engine/simulate", req)
      .then((r) => r.data),

  /** Mark action as applied */
  applyAction: (id: string) =>
    apiClient
      .post<{
        id: string;
        status: ActionStatus;
      }>(`/decision-engine/actions/${id}/apply`)
      .then((r) => r.data),

  /** Dismiss action */
  dismissAction: (id: string) =>
    apiClient
      .post<{
        id: string;
        status: ActionStatus;
      }>(`/decision-engine/actions/${id}/dismiss`)
      .then((r) => r.data),

  /** Latest weekly brief */
  getBrief: () =>
    apiClient.get<WeeklyBrief>("/decision-engine/brief").then((r) => r.data),

  /** Generate brief on demand */
  generateBrief: () =>
    apiClient
      .post<WeeklyBrief>("/decision-engine/brief/generate")
      .then((r) => r.data),
};
