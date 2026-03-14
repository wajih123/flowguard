import { useQuery } from "@tanstack/react-query";
import { transactionsApi } from "@/api/transactions";

export const useTransactions = (
  accountId: string,
  params?: { from?: string; to?: string; category?: string },
) =>
  useQuery({
    queryKey: ["transactions", accountId, params],
    queryFn: () => transactionsApi.list(accountId, params),
    enabled: !!accountId,
    staleTime: 5 * 60 * 1000,
  });

export const useRecurringTransactions = (accountId: string) =>
  useQuery({
    queryKey: ["transactions", accountId, "recurring"],
    queryFn: () => transactionsApi.recurring(accountId),
    enabled: !!accountId,
    staleTime: 30 * 60 * 1000,
  });
