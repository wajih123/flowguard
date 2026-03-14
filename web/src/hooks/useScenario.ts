import { useMutation } from "@tanstack/react-query";
import { scenarioApi } from "@/api/scenarios";
import type { ScenarioRequest } from "@/domain/Scenario";

export const useScenario = () =>
  useMutation({
    mutationFn: (data: ScenarioRequest) => scenarioApi.run(data),
    gcTime: 0,
  });
