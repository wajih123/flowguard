export type AlertType =
  | "CASH_SHORTAGE"
  | "UNUSUAL_SPEND"
  | "PAYMENT_DUE"
  | "POSITIVE_TREND";
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
