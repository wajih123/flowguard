import apiClient from "./client";

export interface ForecastAccuracy {
  id: string;
  forecastDate: string;
  horizonDays: number;
  predictedBalance: number;
  actualBalance: number | null;
  mae: number | null;
  accuracyPct: number | null;
  recordedAt: string;
}

export interface ForecastAccuracySummary {
  averageAccuracyPct: number;
  totalEntries: number;
  reconciled: number;
}

export const forecastAccuracyApi = {
  getAll: () =>
    apiClient
      .get<ForecastAccuracy[]>("/api/forecast-accuracy")
      .then((r) => r.data),

  getByHorizon: (horizonDays: number) =>
    apiClient
      .get<ForecastAccuracy[]>(`/api/forecast-accuracy/${horizonDays}`)
      .then((r) => r.data),

  getSummary: () =>
    apiClient
      .get<ForecastAccuracySummary>("/api/forecast-accuracy/summary")
      .then((r) => r.data),
};
