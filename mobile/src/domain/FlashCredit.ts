export interface FlashCreditRequest {
  accountId: string
  amount: number
  purpose: string
}

export interface FlashCreditResponse {
  creditId: string
  status: 'APPROVED' | 'PENDING' | 'REJECTED'
  amount: number
  message: string
  repaymentDate: string
}
