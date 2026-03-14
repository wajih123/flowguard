export interface ScenarioRequest {
  type: string;
  amount: number;
  delayDays: number;
  description?: string;
}

export interface ScenarioResponse {
  baselineForecast: number[];
  impactedForecast: number[];
  maxImpact: number;
  minBalance: number;
  worstDeficit: number;
  daysUntilImpact: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  recommendation: string;
}
