export type ConnectionStatus = 'ACTIVE' | 'EXPIRED' | 'PENDING'

export interface BankAccount {
  id: string
  iban: string
  balance: number
  currency: 'EUR'
  type: 'PERSONAL' | 'BUSINESS'
  ownerName: string
  bankName: string
  bankLogoUrl?: string
  connectedAt: number
  lastSyncAt?: number
  connectionStatus: ConnectionStatus
}

/** @deprecated use BankAccount */
export type Account = BankAccount
