import apiClient from "./client";

export interface ClientStats {
  clientName: string;
  clientEmail: string | null;
  invoiceCount: number;
  paidInvoiceCount: number;
  totalRevenue: number;
  outstandingAmount: number;
  revenueShare: number;
  avgPaymentDays: number;
  predictedPaymentDays: number;
  isConcentrationRisk: boolean;
}

export const clientStatsApi = {
  list: () =>
    apiClient.get<ClientStats[]>("/api/client-stats").then((r) => r.data),
};
