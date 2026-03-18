import apiClient from "./client";

export interface SectorBenchmark {
  id: string;
  sector: string;
  companySize: string;
  metricName: string;
  p25: number;
  p50: number;
  p75: number;
  unit: string;
}

export interface UserBenchmark {
  sector: string;
  companySize: string;
  metricName: string;
  userValue: number;
  unit: string;
  p25: number;
  p50: number;
  p75: number;
  percentileBand: "BOTTOM_25" | "Q1_Q2" | "Q2_Q3" | "TOP_25" | "UNKNOWN";
  insight: string;
}

export const benchmarksApi = {
  getSectors: () =>
    apiClient.get<string[]>("/api/benchmarks/sectors").then((r) => r.data),

  getBenchmarks: (sector: string, companySize: string) =>
    apiClient
      .get<SectorBenchmark[]>(`/api/benchmarks/${sector}/${companySize}`)
      .then((r) => r.data),

  compare: (sector: string, companySize: string) =>
    apiClient
      .get<UserBenchmark[]>(`/api/benchmarks/compare/${sector}/${companySize}`)
      .then((r) => r.data),
};
