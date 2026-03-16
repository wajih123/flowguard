import type { DailyBalance } from './TreasuryForecast';

export type ScenarioType =
  | 'LATE_PAYMENT'
  | 'EXTRA_EXPENSE'
  | 'EARLY_INVOICE'
  | 'NEW_EXPENSE'
  | 'DELAYED_INCOME'
  | 'LOST_CLIENT'
  | 'NEW_HIRE'
  | 'INVESTMENT'

export interface ScenarioRequest {
  accountId?: string
  type: ScenarioType
  amount: number
  delayDays: number
  description?: string
}

export interface ScenarioResult {
  scenarioType: ScenarioType
  description: string
  // Object-form (backend v1)
  baselinePredictions: DailyBalance[]
  impactedPredictions: DailyBalance[]
  worstDeficit: number
  daysUntilImpact: number
  recommendedAction: string
  recommendation?: string
  riskLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  // Array-form aliases used by ImpactChart
  baselineForecast: number[]
  impactedForecast: number[]
  maxImpact: number
  minBalance: number
}
