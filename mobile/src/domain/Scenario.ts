import type { DailyBalance } from './TreasuryForecast'

export type ScenarioType = 'LATE_PAYMENT' | 'EXTRA_EXPENSE' | 'EARLY_INVOICE'

export interface ScenarioRequest {
  accountId: string
  type: ScenarioType
  amount: number
  delayDays: number
}

export interface ScenarioResult {
  scenarioType: ScenarioType
  description: string
  baselinePredictions: DailyBalance[]
  impactedPredictions: DailyBalance[]
  worstDeficit: number
  daysUntilImpact: number
  recommendedAction: string
}
