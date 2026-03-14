import apiClient from "./client";
import type { Prediction } from "@/types";

export const predictionsApi = {
  getLatest: (accountId: string) =>
    apiClient
      .get<Prediction>("/api/predictions/latest", { params: { accountId } })
      .then((r) => r.data),

  generate: () =>
    apiClient.post<Prediction>("/api/predictions/generate").then((r) => r.data),

  getHistory: () =>
    apiClient.get<Prediction[]>("/api/predictions/history").then((r) => r.data),
};
