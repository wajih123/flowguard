import { useQuery } from "@tanstack/react-query";
import { spendingApi } from "@/api/spending";
import { subDays, format } from "date-fns";

export const useSpending = (accountId: string, periodDays: number = 30) => {
  const to = format(new Date(), "yyyy-MM-dd");
  const from = format(subDays(new Date(), periodDays), "yyyy-MM-dd");
  return useQuery({
    queryKey: ["spending", accountId, periodDays],
    queryFn: () => spendingApi.get(accountId, { from, to }),
    staleTime: 2 * 60 * 60 * 1000, // 2 hours
    enabled: !!accountId,
  });
};
