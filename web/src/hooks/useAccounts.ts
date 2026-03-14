import { useQuery } from "@tanstack/react-query";
import { accountsApi } from "@/api/accounts";

export const useAccounts = () =>
  useQuery({
    queryKey: ["accounts"],
    queryFn: accountsApi.list,
    staleTime: 10 * 60 * 1000,
  });

export const useAccount = (accountId: string) =>
  useQuery({
    queryKey: ["accounts", accountId],
    queryFn: () => accountsApi.get(accountId),
    enabled: !!accountId,
    staleTime: 10 * 60 * 1000,
  });
