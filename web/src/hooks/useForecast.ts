import { useQuery } from "@tanstack/react-query";
import { forecastApi } from "@/api/forecast";

export const useForecast = (horizonDays: number = 30) =>
  useQuery({
    queryKey: ["forecast", horizonDays],
    queryFn: () => forecastApi.get(horizonDays),
    staleTime: 4 * 60 * 60 * 1000, // 4 hours
  });
