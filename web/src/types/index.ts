export interface User {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "ROLE_USER" | "ROLE_BUSINESS" | "ROLE_ADMIN" | "ROLE_SUPER_ADMIN";
}

export interface BankAccount {
  id: string;
  bankName: string;
  ibanMasked: string;
  currentBalance: number;
  currency: string;
  lastSyncAt: string;
  syncStatus: "PENDING" | "SYNCING" | "OK" | "ERROR";
}

export interface DashboardData {
  account: BankAccount;
  currentBalance: number;
  predictedBalance30d: number;
  balanceTrend: number;
  healthScore: number;
  healthLabel: string;
  reserveAvailable: boolean;
  reserveMaxAmount: number;
  hasHighAlert: boolean;
  highAlertMessage?: string;
  highAlertAmount?: number;
  highAlertDate?: string;
  /** Number of active accounts aggregated — used to show multi-account label */
  accountCount?: number;
}

export interface Transaction {
  id: string;
  label: string;
  amount: number;
  currency: string;
  transactionDate: string;
  category: string;
  isRecurring: boolean;
}

export interface PredictionDay {
  date: string;
  balance: number;
  p25: number;
  p75: number;
}

export interface CriticalPoint {
  date: string;
  amount: number;
  type: string;
  label: string;
}

export interface Prediction {
  id: string;
  status: "PENDING" | "PROCESSING" | "READY" | "ERROR";
  horizonDays: number;
  confidenceScore: number;
  confidenceLabel: "Fiable" | "Indicatif" | "Estimation";
  estimatedErrorEur: number;
  minPredictedBalance: number;
  minPredictedDate: string;
  deficitPredicted: boolean;
  deficitAmount?: number;
  deficitDate?: string;
  dailyData: PredictionDay[];
  criticalPoints: CriticalPoint[];
}

export interface Alert {
  id: string;
  severity: "HIGH" | "MEDIUM" | "LOW" | "INFO";
  type: string;
  title: string;
  message: string;
  predictedDeficitAmount?: number;
  predictedDeficitDate?: string;
  isRead: boolean;
  createdAt: string;
}

export interface ReserveActivationRequest {
  accountId: string;
  amount: number;
  reason: string;
}
