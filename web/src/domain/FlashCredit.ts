export type CreditStatus =
  | "PENDING"
  | "APPROVED"
  | "DISBURSED"
  | "REPAID"
  | "OVERDUE"
  | "REJECTED"
  | "RETRACTED";

export interface FlashCredit {
  id: string;
  amount: number;
  fee: number;
  totalRepayment: number;
  taegPercent: number;
  purpose: string;
  status: CreditStatus;
  dueDate?: string;
  retractionDeadline?: string;
  retractionExercised: boolean;
  createdAt: string;
}

export interface FlashCreditRequest {
  amount: number;
  purpose: string;
}
