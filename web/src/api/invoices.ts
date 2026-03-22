import apiClient from "./client";

export interface Invoice {
  id: string;
  clientName: string;
  clientEmail: string | null;
  number: string;
  amountHt: number;
  vatRate: number;
  vatAmount: number;
  totalTtc: number;
  currency: string;
  status: "DRAFT" | "SENT" | "OVERDUE" | "PAID" | "CANCELLED";
  issueDate: string;
  dueDate: string;
  paidAt: string | null;
  notes: string | null;
  createdAt: string;
  daysOverdue: number | null;
  reminderEnabled: boolean;
}

export interface CreateInvoiceRequest {
  clientName: string;
  clientEmail?: string;
  number: string;
  amountHt: number;
  vatRate: number;
  currency: string;
  issueDate: string;
  dueDate: string;
  notes?: string;
}

export const invoicesApi = {
  list: () => apiClient.get<Invoice[]>("/api/invoices").then((r) => r.data),

  get: (id: string) =>
    apiClient.get<Invoice>(`/api/invoices/${id}`).then((r) => r.data),

  create: (data: CreateInvoiceRequest) =>
    apiClient.post<Invoice>("/api/invoices", data).then((r) => r.data),

  send: (id: string) =>
    apiClient.post<Invoice>(`/api/invoices/${id}/send`).then((r) => r.data),

  markPaid: (id: string) =>
    apiClient
      .post<Invoice>(`/api/invoices/${id}/mark-paid`)
      .then((r) => r.data),

  cancel: (id: string) =>
    apiClient.post<Invoice>(`/api/invoices/${id}/cancel`).then((r) => r.data),

  toggleReminder: (id: string) =>
    apiClient
      .post<Invoice>(`/api/invoices/${id}/toggle-reminder`)
      .then((r) => r.data),
};
