import apiClient from "./client";
import type { FinancialKpis } from "@/domain/Kpi";

export const kpisApi = {
  get: () =>
    apiClient.get<FinancialKpis>("/api/financial-kpis").then((r) => r.data),
  exportFec: (from?: string, to?: string) =>
    apiClient
      .get("/api/export/fec", { params: { from, to }, responseType: "blob" })
      .then((r) => r.data),
};
