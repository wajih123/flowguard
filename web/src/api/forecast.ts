import apiClient from "./client";
import type { TreasuryForecast } from "@/domain/TreasuryForecast";

export const forecastApi = {
  get: (horizonDays: number = 30) =>
    apiClient
      .get<TreasuryForecast>("/api/treasury/forecast", {
        params: { horizon: horizonDays },
      })
      .then((r) => r.data),
};
