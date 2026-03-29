import apiClient from "./client";

export interface HiddenSubscription {
  label: string;
  monthlyAmount: number;
  monthsDetected: number;
}

export interface SpendingPatternDto {
  dailyAverage: number;
  todayTotal: number;
  todayVsAvgRatio: number;
  weekdayDailyAverage: number;
  weekendDailyAverage: number;
  weekendVsWeekdayRatio: number;
  todayIsAnomaly: boolean;
  weekendIsAnomaly: boolean;
  hiddenSubscriptions: HiddenSubscription[];
}

export const spendingPatternApi = {
  get: () =>
    apiClient
      .get<SpendingPatternDto>("/api/spending/patterns")
      .then((r) => r.data),
};
