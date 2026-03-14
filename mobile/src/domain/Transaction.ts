export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
}

export type TransactionType = 'DEBIT' | 'CREDIT'

export type TransactionCategory =
  | 'LOYER'
  | 'SALAIRE'
  | 'ALIMENTATION'
  | 'TRANSPORT'
  | 'ABONNEMENT'
  | 'ENERGIE'
  | 'TELECOM'
  | 'ASSURANCE'
  | 'CHARGES_FISCALES'
  | 'FOURNISSEUR'
  | 'CLIENT_PAYMENT'
  | 'VIREMENT'
  | 'AUTRE'

export interface Transaction {
  id: string
  accountId: string
  amount: number
  type: TransactionType
  label: string
  creditorName?: string
  category: TransactionCategory
  date: string
  bookingDate?: string
  isRecurring: boolean
  status: 'BOOKED' | 'PENDING'
}
