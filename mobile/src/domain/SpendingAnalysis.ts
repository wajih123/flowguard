import type { TransactionCategory } from './Transaction'

export interface SpendingCategory {
  name: string
  label: string
  amount: number
  percentage: number
  color?: string
}

export interface SpendingAnalysis {
  accountId: string
  period: string
  totalSpent: number
  byCategory: Partial<Record<TransactionCategory, number>>
  insights: string[]
}
