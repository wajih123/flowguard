export type UserType = "INDIVIDUAL" | "FREELANCE" | "TPE" | "PME" | "SME";
export type KycStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "APPROVED"
  | "VERIFIED"
  | "REJECTED";
export type Role =
  | "ROLE_USER"
  | "ROLE_BUSINESS"
  | "ROLE_ADMIN"
  | "ROLE_SUPER_ADMIN";
export type Plan = "FREE" | "PRO" | "SCALE";

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyName?: string;
  userType: UserType;
  kycStatus: KycStatus;
  role: Role;
  plan: Plan;
  createdAt: string;
  emailVerified: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  companyName?: string;
  userType: UserType;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface MfaChallenge {
  mfaRequired: true;
  sessionToken: string;
  maskedEmail: string;
}

export type LoginResult = AuthResponse | MfaChallenge;

export const isMfaChallenge = (r: LoginResult): r is MfaChallenge =>
  (r as MfaChallenge).mfaRequired === true;

// ── Email verification (one-time, after registration) ─────────────────────────
export interface EmailVerificationPending {
  pendingVerification: true;
  maskedEmail: string;
}

export type RegisterResult = AuthResponse | EmailVerificationPending;

export const isEmailVerificationPending = (
  r: RegisterResult,
): r is EmailVerificationPending =>
  (r as EmailVerificationPending).pendingVerification === true;
