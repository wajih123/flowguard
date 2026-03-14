import apiClient from "./client";
import type { FlashCredit, FlashCreditRequest } from "@/domain/FlashCredit";

export const flashCreditApi = {
  list: () =>
    apiClient.get<FlashCredit[]>("/api/flash-credit").then((r) => r.data),

  request: (data: FlashCreditRequest) =>
    apiClient.post<FlashCredit>("/api/flash-credit", data).then((r) => r.data),

  retract: (creditId: string) =>
    apiClient
      .post<FlashCredit>(`/api/flash-credit/${creditId}/retract`)
      .then((r) => r.data),

  repay: (creditId: string) =>
    apiClient
      .post<FlashCredit>(`/api/flash-credit/${creditId}/repay`)
      .then((r) => r.data),
};
