import { useQuery } from '@tanstack/react-query'
import * as flowguardApi from '../api/flowguardApi'
import type { SpendingAnalysis } from '../domain/SpendingAnalysis'

export const useSpending = (accountId: string | undefined, period: string = '1m') => {
  const query = useQuery<SpendingAnalysis>({
    queryKey: ['spending', accountId, period],
    queryFn: () => flowguardApi.getSpendingAnalysis(accountId!, period),
    enabled: !!accountId,
    staleTime: 2 * 60 * 60 * 1000,
    retry: 2,
  })

  return {
    spending: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  }
}
