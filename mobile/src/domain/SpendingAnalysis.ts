import type { TransactionCategory } from './Transaction'

export interface SpendingAnalysis {
  accountId: string
  period: string
  totalSpent: number
  byCategory: Partial<Record<TransactionCategory, number>>
  insights: string[]
}
