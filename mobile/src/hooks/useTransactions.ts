import { useInfiniteQuery } from '@tanstack/react-query';
import * as flowguardApi from '../api/flowguardApi';
import type { Transaction } from '../domain/Transaction';
import type { Page } from '../domain/Transaction';

export const useTransactions = (accountId: string | undefined) => {
  const query = useInfiniteQuery<Page<Transaction>>({
    queryKey: ['transactions', accountId],
    queryFn: ({ pageParam = 0 }) =>
      flowguardApi.getTransactions(accountId!, {
        page: pageParam as number,
        size: 20,
      }),
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    initialPageParam: 0,
    enabled: !!accountId,
    staleTime: 5 * 60 * 1000,
  });

  const transactions = query.data?.pages.flatMap((p) => p.content) ?? [];

  return {
    transactions,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    fetchNextPage: query.fetchNextPage,
    hasNextPage: !!query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    refetch: query.refetch,
  };
};
