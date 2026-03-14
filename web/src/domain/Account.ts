export type AccountStatus = "ACTIVE" | "SUSPENDED" | "CLOSED";

export interface Account {
  id: string;
  iban: string;
  bic?: string;
  balance: number;
  currency: string;
  bankName?: string;
  status: AccountStatus;
}
