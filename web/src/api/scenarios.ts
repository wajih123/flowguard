import apiClient from "./client";
import type { ScenarioRequest, ScenarioResponse } from "@/domain/Scenario";

export const scenarioApi = {
  run: (data: ScenarioRequest) =>
    apiClient.post<ScenarioResponse>("/api/scenario", data).then((r) => r.data),
};
