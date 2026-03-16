export type { BankAccount } from './Account';

export interface Institution {
  id: string
  name: string
  logoUrl: string
  bic?: string
}

export interface EligibilityResponse {
  eligible: boolean
  reason?: string
  maxAmount: number
  suggestedAmount: number
  nextPositiveDate?: string
}

export interface CreditResponse {
  creditId: string
  status: 'APPROVED' | 'PENDING' | 'REJECTED'
  amount: number
  commission: number
  totalToRepay: number
  repaymentDate: string
  message: string
}

export interface SystemConfig {
  maintenanceMode: boolean
  reserveMaxAmount: number
  reserveMinAmount: number
  reserveCommissionRate: number
}
