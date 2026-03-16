import { useQuery } from '@tanstack/react-query';
import * as flowguardApi from '../api/flowguardApi';
import type { TreasuryForecast } from '../domain/TreasuryForecast';

export const useForecast = (accountId: string | undefined, horizon: number = 30) => {
  const query = useQuery<TreasuryForecast>({
    queryKey: ['forecast', accountId, horizon],
    queryFn: () => flowguardApi.getTreasuryForecast(accountId!, horizon),
    enabled: !!accountId,
    staleTime: 4 * 60 * 60 * 1000,
    retry: 2,
  });

  return {
    forecast: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
  };
};
