import { useQuery } from "@tanstack/react-query";
import { addDays, format } from "date-fns";
import { predictionsApi } from "@/api/predictions";
import type { Prediction, PredictionDay, CriticalPoint } from "@/types";

function generateDailyData(days: number): PredictionDay[] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // Scheduled financial events (day offset → amount)
  // Starting balance 870 is the liquid balance accounting for pending debits.
  // URSSAF on day 5 brings it to exactly -230.
  const events: Record<number, number> = {
    5: -850, // URSSAF trimestrielle
    12: -1200, // Loyer mars
    20: 2400, // Virement client Dubois SARL
    28: 2100, // Facture client Martin
    35: -850, // URSSAF (Q2)
    50: 3500, // Gros contrat
    70: -1200, // Loyer avril
    82: -900, // Charges sociales
  };

  let balance = 870;
  const dailyBurn = -50;
  const result: PredictionDay[] = [];

  for (let d = 0; d < days; d++) {
    balance += dailyBurn;
    if (events[d] !== undefined) balance += events[d];

    const date = new Date(today);
    date.setDate(date.getDate() + d);

    result.push({
      date: format(date, "yyyy-MM-dd"),
      balance: Math.round(balance),
      p25: Math.round(balance - 250),
      p75: Math.round(balance + 250),
    });
  }
  return result;
}

const _today = new Date();
_today.setHours(0, 0, 0, 0);

const criticalPoints: CriticalPoint[] = [
  {
    date: format(addDays(_today, 5), "yyyy-MM-dd"),
    amount: -850,
    type: "URSSAF",
    label: "URSSAF trimestrielle",
  },
  {
    date: format(addDays(_today, 12), "yyyy-MM-dd"),
    amount: -1200,
    type: "RENT",
    label: "Loyer mars",
  },
  {
    date: format(addDays(_today, 20), "yyyy-MM-dd"),
    amount: 2400,
    type: "INVOICE",
    label: "Virement client Dubois",
  },
];

const mockPrediction: Prediction = {
  id: "pred-001",
  status: "READY",
  horizonDays: 90,
  confidenceScore: 0.82,
  confidenceLabel: "Fiable",
  estimatedErrorEur: 120,
  minPredictedBalance: -230,
  minPredictedDate: format(addDays(_today, 5), "yyyy-MM-dd"),
  deficitPredicted: true,
  deficitAmount: -230,
  deficitDate: format(addDays(_today, 5), "yyyy-MM-dd"),
  dailyData: generateDailyData(90),
  criticalPoints,
};

const isMock = import.meta.env.VITE_MOCK_DATA === "true";

export const usePredictions = (accountId?: string) =>
  useQuery<Prediction>({
    queryKey: ["predictions", accountId],
    queryFn: isMock
      ? () =>
          new Promise<Prediction>((resolve) =>
            setTimeout(() => resolve(mockPrediction), 800),
          )
      : () => predictionsApi.getLatest(accountId ?? ""),
    enabled: isMock || !!accountId,
    staleTime: 4 * 60 * 60 * 1000,
  });
