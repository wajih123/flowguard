// Admin API calls — ROLE_ADMIN and ROLE_SUPER_ADMIN.

import apiClient from "./client";
import type { User } from "@/domain/User";
import type { FlashCredit } from "@/domain/FlashCredit";
import type { Alert } from "@/domain/Alert";

// ── Shared types ──────────────────────────────────────────────────────────────

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface AdminStats {
  totalUsers: number;
  activeUsers: number;
  pendingKyc: number;
  totalCreditsAmount: number;
  pendingCredits: number;
  overdueCredits: number;
  criticalAlerts: number;
}

export interface AdminKpi {
  totalUsers: number;
  activeUsers: number;
  pendingKyc: number;
  approvedKyc: number;
  rejectedKyc: number;
  totalCredits: number;
  pendingCredits: number;
  activeCredits: number;
  overdueCredits: number;
  totalCreditVolume: number;
  totalCreditFees: number;
  criticalAlerts: number;
  unreadAlerts: number;
  mlModelAccuracy: number;
  generatedAt: string;
}

export interface AdminUser {
  id: string;
  firstName: string;
  lastName: string;
  emailMasked: string;
  companyName?: string;
  userType: string;
  kycStatus: string;
  role: string;
  disabled: boolean;
  createdAt: string;
}

export interface AdminUserDetail {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  companyName?: string;
  userType: string;
  kycStatus: string;
  role: string;
  swanOnboardingId?: string;
  swanAccountId?: string;
  nordigenRequisitionId?: string;
  gdprConsentAt?: string;
  dataDeletionRequestedAt?: string;
  disabled: boolean;
  disabledAt?: string;
  disabledReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCredit {
  id: string;
  userId: string;
  userEmailMasked: string;
  userFullName: string;
  amount: number;
  fee: number;
  totalRepayment: number;
  taegPercent?: number;
  purpose: string;
  status: string;
  dueDate: string;
  disbursedAt?: string;
  repaidAt?: string;
  retractionDeadline?: string;
  retractionExercised: boolean;
  createdAt: string;
}

export interface CreditStats {
  total: number;
  pending: number;
  active: number;
  overdue: number;
  repaid: number;
  rejected: number;
  volumeTotal: number;
  volumePending: number;
  feesCollected: number;
}

export interface AppLog {
  id: number;
  level: "DEBUG" | "INFO" | "WARN" | "ERROR";
  logger: string;
  message: string;
  stackTrace?: string;
  createdAt: string;
}

export interface FeatureFlag {
  id: string;
  flagKey: string;
  enabled: boolean;
  description?: string;
  updatedAt: string;
}

export interface SystemConfig {
  id: string;
  configKey: string;
  configValue: string;
  valueType: string;
  description?: string;
  updatedAt: string;
}

export interface AuditEntry {
  id: string;
  actorId?: string;
  actorEmail?: string;
  actorRole?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  ipAddress?: string;
  createdAt: string;
}

// ── API ───────────────────────────────────────────────────────────────────────

export const adminApi = {
  // ── Users ──────────────────────────────────────────────────────────────────
  listUsers: (params?: {
    page?: number;
    size?: number;
    search?: string;
    kycStatus?: string;
    userType?: string;
  }) =>
    apiClient
      .get<Page<AdminUser>>("/api/admin/users", { params })
      .then((r) => r.data),

  getUser: (userId: string) =>
    apiClient
      .get<AdminUserDetail>(`/api/admin/users/${userId}`)
      .then((r) => r.data),

  updateKycStatus: (userId: string, status: string) =>
    apiClient
      .put<AdminUserDetail>(`/api/admin/users/${userId}/kyc`, { status })
      .then((r) => r.data),

  disableUser: (userId: string, reason?: string) =>
    apiClient
      .put<AdminUserDetail>(`/api/admin/users/${userId}/disable`, { reason })
      .then((r) => r.data),

  enableUser: (userId: string) =>
    apiClient
      .put<AdminUserDetail>(`/api/admin/users/${userId}/enable`)
      .then((r) => r.data),

  gdprDeleteUser: (userId: string) =>
    apiClient.delete(`/api/admin/users/${userId}/gdpr`).then((r) => r.data),

  // ── Flash Credits ──────────────────────────────────────────────────────────
  listCredits: (params?: { page?: number; size?: number; status?: string }) =>
    apiClient
      .get<Page<AdminCredit>>("/api/admin/flash-credits", { params })
      .then((r) => r.data),

  getCredit: (creditId: string) =>
    apiClient
      .get<AdminCredit>(`/api/admin/flash-credits/${creditId}`)
      .then((r) => r.data),

  getCreditStats: () =>
    apiClient
      .get<CreditStats>("/api/admin/flash-credits/stats")
      .then((r) => r.data),

  approveCredit: (creditId: string) =>
    apiClient
      .put<AdminCredit>(`/api/admin/flash-credits/${creditId}/approve`)
      .then((r) => r.data),

  rejectCredit: (creditId: string, reason: string) =>
    apiClient
      .put<AdminCredit>(`/api/admin/flash-credits/${creditId}/reject`, {
        reason,
      })
      .then((r) => r.data),

  writeOffCredit: (creditId: string) =>
    apiClient
      .put<AdminCredit>(`/api/admin/flash-credits/${creditId}/written-off`)
      .then((r) => r.data),

  // ── Alerts ─────────────────────────────────────────────────────────────────
  listAlerts: (params?: { page?: number; size?: number; severity?: string }) =>
    apiClient
      .get<Page<Alert>>("/api/admin/alerts", { params })
      .then((r) => r.data),

  // ── KPIs ───────────────────────────────────────────────────────────────────
  getKpis: () => apiClient.get<AdminKpi>("/api/admin/kpis").then((r) => r.data),

  // ── Stats ──────────────────────────────────────────────────────────────────
  getStats: () =>
    apiClient.get<AdminStats>("/api/admin/stats").then((r) => r.data),

  // ── Application Logs ───────────────────────────────────────────────────────
  getLogs: (params?: { page?: number; size?: number; level?: string }) =>
    apiClient
      .get<Page<AppLog>>("/api/admin/logs", { params })
      .then((r) => r.data),

  // ── Feature Flags ──────────────────────────────────────────────────────────
  listFlags: () =>
    apiClient.get<FeatureFlag[]>("/api/admin/flags").then((r) => r.data),

  updateFlag: (flagKey: string, enabled: boolean) =>
    apiClient
      .put(`/api/admin/flags/${flagKey}`, { enabled })
      .then((r) => r.data),

  // ── System Config ──────────────────────────────────────────────────────────
  listConfig: () =>
    apiClient.get<SystemConfig[]>("/api/admin/config").then((r) => r.data),

  updateConfig: (configKey: string, value: string) =>
    apiClient
      .put(`/api/admin/config/${configKey}`, { value })
      .then((r) => r.data),

  // ── Super-Admin: Admin Management ──────────────────────────────────────────
  listAdmins: () =>
    apiClient
      .get<AdminUserDetail[]>("/api/super-admin/admins")
      .then((r) => r.data),

  promoteUser: (userId: string, role: "ROLE_ADMIN" | "ROLE_SUPER_ADMIN") =>
    apiClient
      .post<AdminUserDetail>(`/api/super-admin/admins/${userId}/promote`, {
        role,
      })
      .then((r) => r.data),

  revokeAdmin: (userId: string) =>
    apiClient
      .post<AdminUserDetail>(`/api/super-admin/admins/${userId}/revoke`)
      .then((r) => r.data),

  // ── Super-Admin: Audit Log ─────────────────────────────────────────────────
  getAuditLog: (params?: {
    page?: number;
    size?: number;
    actorId?: string;
    action?: string;
  }) =>
    apiClient
      .get<Page<AuditEntry>>("/api/super-admin/audit", { params })
      .then((r) => r.data),

  // ── Super-Admin: ML ────────────────────────────────────────────────────────
  triggerMlRetrain: () =>
    apiClient.post("/api/super-admin/ml/retrain").then((r) => r.data),
};
