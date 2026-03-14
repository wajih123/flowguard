export type TransactionType = "DEBIT" | "CREDIT";
export type TransactionCategory =
  | "LOYER"
  | "SALAIRE"
  | "ALIMENTATION"
  | "TRANSPORT"
  | "ABONNEMENT"
  | "ENERGIE"
  | "TELECOM"
  | "ASSURANCE"
  | "CHARGES_FISCALES"
  | "FOURNISSEUR"
  | "CLIENT_PAYMENT"
  | "VIREMENT"
  | "AUTRE";

export interface Transaction {
  id: string;
  accountId: string;
  amount: number;
  type: TransactionType;
  label: string;
  category: TransactionCategory;
  date: string;
  isRecurring: boolean;
}
