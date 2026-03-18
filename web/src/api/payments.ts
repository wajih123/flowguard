import apiClient from "./client";

export interface PaymentInitiation {
  id: string;
  creditorName: string;
  creditorIban: string;
  amount: number;
  currency: string;
  reference: string | null;
  status: "PENDING" | "SUBMITTED" | "EXECUTED" | "REJECTED" | "CANCELLED";
  swanPaymentId: string | null;
  initiatedAt: string;
  executedAt: string | null;
}

export interface InitiatePaymentRequest {
  creditorName: string;
  creditorIban: string;
  amount: number;
  currency: string;
  reference?: string;
}

export const paymentsApi = {
  list: () =>
    apiClient.get<PaymentInitiation[]>("/api/payments").then((r) => r.data),

  initiate: (data: InitiatePaymentRequest, idempotencyKey: string) =>
    apiClient
      .post<PaymentInitiation>("/api/payments", data, {
        headers: { "Idempotency-Key": idempotencyKey },
      })
      .then((r) => r.data),

  cancel: (id: string) =>
    apiClient
      .post<PaymentInitiation>(`/api/payments/${id}/cancel`)
      .then((r) => r.data),
};
