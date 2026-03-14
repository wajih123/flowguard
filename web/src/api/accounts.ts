import apiClient from "./client";
import type { Account } from "@/domain/Account";

export const accountsApi = {
  list: () => apiClient.get<Account[]>("/api/accounts").then((r) => r.data),
  get: (accountId: string) =>
    apiClient.get<Account>(`/api/accounts/${accountId}`).then((r) => r.data),
};
