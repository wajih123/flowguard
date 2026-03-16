import type { TransactionCategory } from './Transaction';

export interface SpendingCategory {
  category: string
  name: string
  label: string
  amount: number
  percentage: number
  color?: string
}

export interface AiInsight {
  type: 'positive' | 'negative' | 'neutral'
  text: string
}

export interface TopMerchant {
  name: string
  totalAmount: number
  transactionCount: number
}

export interface SpendingAnalysis {
  accountId: string
  period: string
  totalSpent: number
  totalAmount: number
  byCategory: Partial<Record<TransactionCategory, number>>
  categories: SpendingCategory[]
  insights: string[]
  aiInsights?: AiInsight[]
  topMerchants?: TopMerchant[]
}
