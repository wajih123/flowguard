import apiClient from "./client";

export interface BnplInstallment {
  id: string;
  merchantLabel: string;
  provider: string;
  installmentAmount: number;
  installmentsTotal: number;
  installmentsPaid: number;
  remainingAmount: number;
  nextDebitDate: string | null;
}

export interface LoanAmortization {
  id: string;
  lenderLabel: string;
  monthlyPayment: number;
  paymentsMade: number;
  firstPaymentDate: string | null;
  estimatedPayoffDate: string | null;
  yearsUntilPayoff?: number;
  daysUntilPayoff?: number;
}

export const bnplApi = {
  list: () =>
    apiClient
      .get<{
        installments: BnplInstallment[];
        totalRemainingDebt: number;
        count: number;
      }>("/api/bnpl")
      .then((r) => r.data),

  detect: () => apiClient.post("/api/bnpl/detect").then((r) => r.data),
};

export const loansApi = {
  list: () =>
    apiClient
      .get<{
        loans: LoanAmortization[];
        totalMonthlyBurden: number;
        count: number;
      }>("/api/loans")
      .then((r) => r.data),

  detect: () => apiClient.post("/api/loans/detect").then((r) => r.data),
};
