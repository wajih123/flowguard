import apiClient from "./client";

export interface CashCalendarEvent {
  date: string;
  type:
    | "INVOICE_DUE"
    | "INVOICE_OVERDUE"
    | "INVOICE_SCHEDULED"
    | "RECURRING_CHARGE"
    | "RECURRING_INCOME";
  label: string;
  amount: number;
  status: "PENDING" | "OVERDUE" | "PREDICTED";
  clientName: string | null;
}

export const cashCalendarApi = {
  list: () =>
    apiClient
      .get<CashCalendarEvent[]>("/api/cash-calendar")
      .then((r) => r.data),
};
