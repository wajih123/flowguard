import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as flowguardApi from '../api/flowguardApi';
import { useAlertStore } from '../store/alertStore';
import type { Alert } from '../domain/Alert';

export const useAlerts = (accountId: string | undefined) => {
  const queryClient = useQueryClient();
  const setUnreadCount = useAlertStore((s) => s.setUnreadCount);

  const query = useQuery<Alert[]>({
    queryKey: ['alerts', accountId],
    queryFn: () => flowguardApi.getAlerts(accountId!).then((p) => p.content ?? []),
    enabled: !!accountId,
    staleTime: 60 * 60 * 1000,
    retry: 2,
  });

  const alerts = query.data ?? [];
  const unreadAlerts = alerts.filter((a) => !a.isRead);
  const criticalAlerts = alerts.filter((a) => a.severity === 'HIGH' || a.severity === 'CRITICAL');

  if (query.isSuccess) {
    setUnreadCount(unreadAlerts.length);
  }

  const markAsReadMutation = useMutation({
    mutationFn: (alertId: string) => flowguardApi.markAlertRead(alertId),
    onMutate: async (alertId: string) => {
      await queryClient.cancelQueries({ queryKey: ['alerts', accountId] });

      const previousAlerts = queryClient.getQueryData<Alert[]>(['alerts', accountId]);

      queryClient.setQueryData<Alert[]>(
        ['alerts', accountId],
        (old) => old?.map((a) => (a.id === alertId ? { ...a, isRead: true } : a)) ?? [],
      );

      return { previousAlerts };
    },
    onError: (_err, _alertId, context) => {
      if (context?.previousAlerts) {
        queryClient.setQueryData(['alerts', accountId], context.previousAlerts);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts', accountId] });
    },
  });

  return {
    alerts,
    unreadAlerts,
    criticalAlerts,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
    markAsRead: markAsReadMutation.mutate,
  };
};

export const useMarkAlertAsRead = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) => flowguardApi.markAlertRead(alertId),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });
};
