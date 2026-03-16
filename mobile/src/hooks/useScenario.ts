import { useMutation } from '@tanstack/react-query';
import * as flowguardApi from '../api/flowguardApi';
import type { ScenarioRequest, ScenarioResult } from '../domain/Scenario';

export const useScenario = () => {
  const mutation = useMutation<ScenarioResult, Error, ScenarioRequest>({
    mutationFn: (req: ScenarioRequest) => flowguardApi.runScenario(req),
    gcTime: 0,
  });

  return {
    runScenario: mutation.mutate,
    isLoading: mutation.isPending,
    result: mutation.data,
    error: mutation.error,
    reset: mutation.reset,
  };
};
