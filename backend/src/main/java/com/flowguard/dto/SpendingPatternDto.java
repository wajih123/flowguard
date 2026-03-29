package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending pattern analysis for a user over the last 90 days.
 * Exposes daily/weekend averages, today's anomaly status, and detected
 * hidden subscriptions (recurring charges not yet tagged as ABONNEMENT).
 */
public record SpendingPatternDto(
        BigDecimal dailyAverage,
        BigDecimal todayTotal,
        double todayVsAvgRatio,
        BigDecimal weekdayDailyAverage,
        BigDecimal weekendDailyAverage,
        double weekendVsWeekdayRatio,
        boolean todayIsAnomaly,
        boolean weekendIsAnomaly,
        List<HiddenSubscriptionDto> hiddenSubscriptions) {

    public record HiddenSubscriptionDto(
            String label,
            BigDecimal monthlyAmount,
            int monthsDetected) {}
}
