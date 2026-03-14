export type AlertType = 'CASH_SHORTAGE' | 'UNUSUAL_SPEND' | 'PAYMENT_DUE' | 'POSITIVE_TREND'

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Alert {
  id: string
  accountId: string
  type: AlertType
  severity: AlertSeverity
  message: string
  projectedDeficit?: number
  triggerDate?: string
  isRead: boolean
  createdAt: number
}
