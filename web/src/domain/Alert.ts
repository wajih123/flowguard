export type AlertType =
  | "CASH_SHORTAGE"
  | "UNUSUAL_SPEND"
  | "PAYMENT_DUE"
  | "POSITIVE_TREND"
  | "EXCESSIVE_SPEND"
  | "HIDDEN_SUBSCRIPTION"
  | "SAVINGS_OPPORTUNITY"
  | "SUBSCRIPTION_PRICE_INCREASE"
  | "FREE_TRIAL_ENDING"
  | "DUPLICATE_SUBSCRIPTION"
  | "BUDGET_RISK";
export type AlertSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface Alert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  message: string;
  /** Plain-language action suggested by the decision engine */
  suggestedAction?: string;
  projectedDeficit?: number;
  triggerDate?: string;
  isRead: boolean;
  createdAt: string;
}

export interface AlertThreshold {
  id: string;
  alertType: AlertType;
  minAmount: number;
  enabled: boolean;
  minSeverity: AlertSeverity;
}

export interface AlertThresholdRequest {
  alertType: AlertType;
  minAmount?: number;
  enabled: boolean;
  minSeverity: AlertSeverity;
}
