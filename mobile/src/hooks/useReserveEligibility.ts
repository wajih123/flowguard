import { useQuery } from '@tanstack/react-query';
import * as flowguardApi from '../api/flowguardApi';
import { useAuthStore } from '../store/authStore';
import { useFeatureFlag } from './useFeatureFlag';
import type { EligibilityResponse } from '../domain/Banking';

export const useReserveEligibility = (accountId: string | undefined) => {
  const reserveEnabled = useFeatureFlag('RESERVE_ENABLED');
  const user = useAuthStore((s) => s.user);
  const kycVerified = user?.kycStatus === 'VERIFIED';

  const enabled = !!accountId && reserveEnabled && kycVerified;

  const query = useQuery<EligibilityResponse>({
    queryKey: ['reserve-eligibility', accountId],
    queryFn: () => flowguardApi.getReserveEligibility(accountId!),
    enabled,
    staleTime: 10 * 60 * 1000,
    retry: 1,
  });

  return {
    eligibility: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    blocked: !reserveEnabled ? ('flag' as const) : !kycVerified ? ('kyc' as const) : null,
    refetch: query.refetch,
  };
};
