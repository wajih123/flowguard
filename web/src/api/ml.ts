import { apiClient } from "./client";

// ── Types ──────────────────────────────────────────────────────────────────────

export interface MlModelVersion {
  version: string;
  mae_7d: number | null;
  mae_30d: number | null;
  mae_90d: number | null;
  deficit_recall: number | null;
  deficit_precision: number | null;
  n_users_trained: number | null;
  status: "CANDIDATE" | "ACTIVE" | "DEPRECATED";
  created_at: string;
}

export interface MlRetrainEvent {
  started_at: string;
  completed_at: string | null;
  reason: string | null;
  status: "RUNNING" | "SUCCESS" | "FAILED";
  n_users: number | null;
  final_mae: number | null;
  error: string | null;
  duration_min: number | null;
}

export interface MlQualityEntry {
  log_date: string;
  mae_7d: number | null;
  mae_30d: number | null;
  drift_ratio_7d: number | null;
  drift_ratio_30d: number | null;
  alert_triggered: boolean;
}

export interface MlStats {
  activeVersion: MlModelVersion | null;
  modelHistory: MlModelVersion[];
  retrainLog: MlRetrainEvent[];
  qualityLog: MlQualityEntry[];
  totalModels: number;
  totalRetrains: number;
}

// ── API calls ─────────────────────────────────────────────────────────────────

export const mlApi = {
  getStats: (): Promise<MlStats> =>
    apiClient.get("/admin/ml/stats").then((r) => r.data),

  triggerRetrain: (): Promise<{ status: string; message: string }> =>
    apiClient.post("/admin/ml/retrain").then((r) => r.data),
};
