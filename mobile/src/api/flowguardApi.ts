import axios from 'axios'
import { setupInterceptors } from './interceptors'
import { fetch } from 'react-native-ssl-pinning'
import { FlowGuardError } from './errors'
import type { AuthResponse, User, RegisterDto, RegisterBusinessDto } from '../domain/User'
import type { TreasuryForecast } from '../domain/TreasuryForecast'
import type { SpendingAnalysis } from '../domain/SpendingAnalysis'
import type { ScenarioRequest, ScenarioResult } from '../domain/Scenario'
import type { Alert } from '../domain/Alert'
import type {
  BankAccount,
  Institution,
  EligibilityResponse,
  CreditResponse,
  SystemConfig,
} from '../domain/Banking'
import type { Transaction, Page } from '../domain/Transaction'

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8080'

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'X-App-Version': '1.0.0',
  },
})

setupInterceptors(api)

const sslHeaders = { 'Content-Type': 'application/json', 'X-App-Version': '1.0.0' }

// ── Auth (SSL pinning) ──────────────────────────────────────────
export const login = async (email: string, password: string): Promise<AuthResponse> => {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: sslHeaders,
    body: JSON.stringify({ email, password }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200) throw new FlowGuardError('UNAUTHORIZED', 'Identifiants incorrects')
  return JSON.parse(res.bodyString) as AuthResponse
}

export const register = async (data: RegisterDto): Promise<AuthResponse> => {
  const res = await fetch(`${BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: sslHeaders,
    body: JSON.stringify(data),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 201 && res.status !== 200)
    throw new FlowGuardError('VALIDATION', "Erreur d'inscription")
  return JSON.parse(res.bodyString) as AuthResponse
}

export const registerBusiness = async (data: RegisterBusinessDto): Promise<AuthResponse> => {
  const res = await fetch(`${BASE_URL}/api/auth/register/business`, {
    method: 'POST',
    headers: sslHeaders,
    body: JSON.stringify(data),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 201 && res.status !== 200)
    throw new FlowGuardError('VALIDATION', "Erreur d'inscription entreprise")
  return JSON.parse(res.bodyString) as AuthResponse
}

export const refreshToken = async (
  refreshTokenValue: string,
): Promise<{ accessToken: string; refreshToken: string }> => {
  const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    headers: sslHeaders,
    body: JSON.stringify({ refreshToken: refreshTokenValue }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200) throw new FlowGuardError('UNAUTHORIZED', 'Session expirée')
  return JSON.parse(res.bodyString) as { accessToken: string; refreshToken: string }
}

export const logoutApi = async (refreshTokenValue: string): Promise<void> => {
  await api.post('/api/auth/logout', { refreshToken: refreshTokenValue })
}

export const getKycUrl = async (): Promise<{ url: string }> => {
  const { data } = await api.get<{ url: string }>('/api/users/kyc-url')
  return data
}

// ── User ──────────────────────────────────────────────────────────
export const getCurrentUser = async (): Promise<User> => {
  const { data } = await api.get<User>('/api/users/me')
  return data
}

export const updateProfile = async (payload: Partial<User>): Promise<User> => {
  const { data } = await api.put<User>('/api/users/me', payload)
  return data
}

export const exportUserData = async (): Promise<{ downloadUrl: string }> => {
  const { data } = await api.post<{ downloadUrl: string }>('/api/users/export')
  return data
}

export const deleteAccount = async (): Promise<void> => {
  await api.delete('/api/users/me')
}

// ── Banking ───────────────────────────────────────────────────────
export const getInstitutions = async (): Promise<Institution[]> => {
  const { data } = await api.get<Institution[]>('/api/banking/institutions')
  return data
}

export const connectBank = async (
  institutionId: string,
): Promise<{ requisitionId: string; connectionUrl: string }> => {
  const { data } = await api.post<{ requisitionId: string; connectionUrl: string }>(
    '/api/banking/connect',
    { institutionId },
  )
  return data
}

export const getBankStatus = async (
  requisitionId: string,
): Promise<{ status: string; accountIds?: string[] }> => {
  const { data } = await api.get<{ status: string; accountIds?: string[] }>(
    `/api/banking/status/${encodeURIComponent(requisitionId)}`,
  )
  return data
}

export const syncBank = async (accountId: string): Promise<{ synced: number; skipped: number }> => {
  const { data } = await api.post<{ synced: number; skipped: number }>('/api/banking/sync', {
    accountId,
  })
  return data
}

export const getAccounts = async (): Promise<BankAccount[]> => {
  const { data } = await api.get<BankAccount[]>('/api/banking/accounts')
  return data
}

// Legacy alias used in accountStore
export const getCurrentAccount = async (): Promise<BankAccount> => {
  const accounts = await getAccounts()
  if (accounts.length === 0) throw new FlowGuardError('NOT_FOUND', 'Aucun compte bancaire')
  return accounts[0] as BankAccount
}

// ── Transactions ─────────────────────────────────────────────────
export const getTransactions = async (
  accountId: string,
  params: {
    page?: number
    size?: number
    category?: string
    dateFrom?: string
    dateTo?: string
  } = {},
): Promise<Page<Transaction>> => {
  const { data } = await api.get<Page<Transaction>>(
    `/api/transactions/${encodeURIComponent(accountId)}`,
    { params },
  )
  return data
}

// ── Treasury/Forecast ───────────────────────────────────────────
export const getTreasuryForecast = async (
  accountId: string,
  horizon: number,
): Promise<TreasuryForecast> => {
  const { data } = await api.get<TreasuryForecast>(
    `/api/treasury/${encodeURIComponent(accountId)}/forecast`,
    { params: { horizon } },
  )
  return data
}

export const getForecast = getTreasuryForecast

export const getSpendingAnalysis = async (
  accountId: string,
  period: string,
): Promise<SpendingAnalysis> => {
  const { data } = await api.get<SpendingAnalysis>(
    `/api/treasury/${encodeURIComponent(accountId)}/spending`,
    { params: { period } },
  )
  return data
}

export const runScenario = async (req: ScenarioRequest): Promise<ScenarioResult> => {
  const { data } = await api.post<ScenarioResult>(
    `/api/treasury/${encodeURIComponent(req.accountId)}/scenario`,
    req,
  )
  return data
}

export const syncTransactions = async (accountId: string): Promise<{ synced: number }> => {
  const { data } = await api.post<{ synced: number }>(
    `/api/treasury/${encodeURIComponent(accountId)}/sync`,
  )
  return data
}

// ── Alerts ───────────────────────────────────────────────────────
export const getAlerts = async (
  accountId: string,
  params: { page?: number; unreadOnly?: boolean; severity?: string } = {},
): Promise<Page<Alert>> => {
  const { data } = await api.get<Page<Alert>>(`/api/alerts/${encodeURIComponent(accountId)}`, {
    params,
  })
  return data
}

export const markAlertRead = async (alertId: string): Promise<void> => {
  await api.post(`/api/alerts/${encodeURIComponent(alertId)}/read`)
}

export const markAllAlertsRead = async (accountId: string): Promise<void> => {
  await api.post(`/api/alerts/${encodeURIComponent(accountId)}/read-all`)
}

export const saveFcmToken = async (accountId: string, token: string): Promise<void> => {
  await api.post(`/api/alerts/${encodeURIComponent(accountId)}/fcm-token`, { token })
}

export const getUnreadCount = async (accountId: string): Promise<{ count: number }> => {
  const { data } = await api.get<{ count: number }>(
    `/api/alerts/${encodeURIComponent(accountId)}/count`,
  )
  return data
}

// ── Reserve / Credit (SSL pinning) ───────────────────────────────
export const getReserveEligibility = async (accountId: string): Promise<EligibilityResponse> => {
  const res = await fetch(`${BASE_URL}/api/credit/eligibility/${encodeURIComponent(accountId)}`, {
    method: 'GET',
    headers: sslHeaders,
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200) throw new FlowGuardError('SERVER', 'Erreur éligibilité')
  return JSON.parse(res.bodyString) as EligibilityResponse
}

export const activateReserve = async (
  accountId: string,
  amount: number,
): Promise<CreditResponse> => {
  const res = await fetch(`${BASE_URL}/api/credit/activate`, {
    method: 'POST',
    headers: sslHeaders,
    body: JSON.stringify({ accountId, amount }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200 && res.status !== 201)
    throw new FlowGuardError('SERVER', "Erreur d'activation de la Réserve")
  return JSON.parse(res.bodyString) as CreditResponse
}

// Legacy Flash Credit (mapped to reserve)
export const requestFlashCredit = activateReserve

// ── Config (public) ───────────────────────────────────────────────
export const getFeatureFlags = async (): Promise<Record<string, boolean>> => {
  const { data } = await api.get<Record<string, boolean>>('/api/config/flags')
  return data
}

export const getSystemConfig = async (): Promise<SystemConfig> => {
  const { data } = await api.get<SystemConfig>('/api/config/system')
  return data
}

// ── Admin ─────────────────────────────────────────────────────────
export const getAdminKpis = async (): Promise<Record<string, number | string>> => {
  const { data } = await api.get<Record<string, number | string>>('/api/admin/kpis')
  return data
}

// ── Error class ────────────────────────────────────────────────────
export { FlowGuardError } from './errors'
export type { ErrorCode as FlowGuardErrorCode } from './errors'

export default api

const api = axios.create({
  baseURL: process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8080',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'X-App-Version': '1.0.0',
  },
})

setupInterceptors(api)

// ── Auth (SSL pinning) ──────────────────────────────────────────
export const login = async (email: string, password: string): Promise<AuthResponse> => {
  const res = await fetch(`${api.defaults.baseURL}/api/auth/login`, {
    method: 'POST',
    headers: api.defaults.headers,
    body: JSON.stringify({ email, password }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200) throw new FlowGuardError('UNAUTHORIZED', 'Erreur de connexion')
  return JSON.parse(res.bodyString)
}

export const register = async (
  email: string,
  password: string,
  userType: UserType,
): Promise<User> => {
  const res = await fetch(`${api.defaults.baseURL}/api/auth/register`, {
    method: 'POST',
    headers: api.defaults.headers,
    body: JSON.stringify({ email, password, userType }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 201) throw new FlowGuardError('VALIDATION', 'Erreur d’inscription')
  return JSON.parse(res.bodyString)
}

export const refreshToken = async (
  refreshTokenValue: string,
): Promise<{ token: string; refreshToken: string }> => {
  const res = await fetch(`${api.defaults.baseURL}/api/auth/refresh`, {
    method: 'POST',
    headers: api.defaults.headers,
    body: JSON.stringify({ refreshToken: refreshTokenValue }),
    sslPinning: { certs: ['flowguard'] },
    timeoutInterval: 30000,
  })
  if (res.status !== 200) throw new FlowGuardError('UNAUTHORIZED', 'Erreur de refresh')
  return JSON.parse(res.bodyString)
}

export const logout = async (): Promise<void> => {
  await api.post('/api/auth/logout')
}

export const getKycUrl = async (): Promise<{ url: string }> => {
  const { data } = await api.get<{ url: string }>('/api/auth/kyc-url')
  return data
}

// ── Treasury ─────────────────────────────────────────────────────
export const getTreasuryForecast = async (
  accountId: string,
  horizon: number,
): Promise<TreasuryForecast> => {
  const { data } = await api.get<TreasuryForecast>(
    `/api/treasury/${encodeURIComponent(accountId)}/forecast`,
    { params: { horizon } },
  )
  return data
}

export const getSpendingAnalysis = async (
  accountId: string,
  period: string,
): Promise<SpendingAnalysis> => {
  const { data } = await api.get<SpendingAnalysis>(
    `/api/treasury/${encodeURIComponent(accountId)}/spending`,
    { params: { period } },
  )
  return data
}

export const runScenario = async (req: ScenarioRequest): Promise<ScenarioResult> => {
  const { data } = await api.post<ScenarioResult>(
    `/api/treasury/${encodeURIComponent(req.accountId)}/scenario`,
    req,
  )
  return data
}

export const syncTransactions = async (accountId: string): Promise<{ synced: number }> => {
  const { data } = await api.post<{ synced: number }>(
    `/api/treasury/${encodeURIComponent(accountId)}/sync`,
  )
  return data
}

// ── Alerts ───────────────────────────────────────────────────────
export const getAlerts = async (accountId: string, unreadOnly?: boolean): Promise<Alert[]> => {
  const { data } = await api.get<Alert[]>(`/api/alerts/${encodeURIComponent(accountId)}`, {
    params: { unreadOnly: unreadOnly ?? false },
  })
  return data
}

export const markAlertRead = async (alertId: string): Promise<void> => {
  await api.post(`/api/alerts/${encodeURIComponent(alertId)}/read`)
}

export const getUnreadCount = async (accountId: string): Promise<{ count: number }> => {
  const { data } = await api.get<{ count: number }>(
    `/api/alerts/${encodeURIComponent(accountId)}/count`,
  )
  return data
}

// ── Banking (Open Banking) ───────────────────────────────────────
export const getInstitutions = async (): Promise<
  { id: string; name: string; logoUrl: string }[]
> => {
  const { data } = await api.get('/api/banking/institutions')
  return data
}

export const connectBank = async (
  institutionId: string,
  redirectUri: string,
): Promise<{ requisitionId: string; connectionUrl: string }> => {
  const { data } = await api.post('/api/banking/connect', { institutionId, redirectUri })
  return data
}

export const getBankStatus = async (
  requisitionId: string,
): Promise<{ status: string; linkedAccountIds?: string[] }> => {
  const { data } = await api.get(`/api/banking/status/${encodeURIComponent(requisitionId)}`)
  return data
}

export const syncBank = async (accountId: string): Promise<{ synced: number; skipped: number }> => {
  const { data } = await api.post('/api/banking/sync', { accountId })
  return data
}

// ── Account ──────────────────────────────────────────────────────
export const getCurrentUser = async (): Promise<User> => {
  const { data } = await api.get<User>('/api/users/me')
  return data
}

export const updateProfile = async (user: Partial<User>): Promise<User> => {
  const { data } = await api.put<User>('/api/users/me', user)
  return data
}

// ── Flash Credit (SSL pinning) ───────────────────────────────────
export const requestFlashCredit = async (req: FlashCreditRequest): Promise<FlashCreditResponse> => {
  const res = await fetch(
    `${api.defaults.baseURL}/api/flash-credit/${encodeURIComponent(req.accountId)}`,
    {
      method: 'POST',
      headers: api.defaults.headers,
      body: JSON.stringify({ amount: req.amount, purpose: req.purpose }),
      sslPinning: { certs: ['flowguard'] },
      timeoutInterval: 30000,
    },
  )
  if (res.status !== 200) throw new FlowGuardError('SERVER', 'Erreur crédit')
  return JSON.parse(res.bodyString)
}

// ── Error class ─────────────────────────────────────────────────-
export type FlowGuardErrorCode =
  | 'UNAUTHORIZED'
  | 'NETWORK'
  | 'SERVER'
  | 'NOT_FOUND'
  | 'VALIDATION'
  | 'UNKNOWN'

export class FlowGuardError extends Error {
  constructor(
    public code: FlowGuardErrorCode,
    message: string,
    public fields?: Record<string, string>,
  ) {
    super(message)
  }
}

export default api
