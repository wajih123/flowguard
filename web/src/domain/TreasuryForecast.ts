export interface ForecastPoint {
  date: string;
  predictedBalance: number;
  lowerBound: number;
  upperBound: number;
}

export interface CriticalPoint {
  date: string;
  predictedBalance: number;
  reason: string;
}

export interface TreasuryForecast {
  predictions: ForecastPoint[];
  criticalPoints: CriticalPoint[];
  confidenceScore: number;
  healthScore: number;
  generatedAt: string;
}
