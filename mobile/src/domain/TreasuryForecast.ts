export interface DailyBalance {
  date: string
  balance: number
}

export interface CriticalPoint {
  date: string
  projectedBalance: number
  urgency: 'IMMINENT' | 'UPCOMING'
  daysUntil: number
}

export interface TreasuryForecast {
  accountId: string
  generatedAt: string
  predictions: DailyBalance[]
  criticalPoints: CriticalPoint[]
  confidence: number
  healthScore: number
}
