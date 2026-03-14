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
}\n\n/** @deprecated use BankAccount */\nexport type Account = BankAccount
