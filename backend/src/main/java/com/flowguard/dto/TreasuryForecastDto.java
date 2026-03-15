package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TreasuryForecastDto(
    List<ForecastPoint> predictions,
    List<CriticalPoint> criticalPoints,
    double confidenceScore,
    double healthScore,
    LocalDate generatedAt,
    /** Which model (or ensemble) produced this forecast. */
    String modelUsed,
    /** The Model Race winner if one was selected, null if blended. */
    String modelRaceWinner
) {
    public record ForecastPoint(
        LocalDate date,
        BigDecimal predictedBalance,
        BigDecimal lowerBound,
        BigDecimal upperBound
    ) {}

    public record CriticalPoint(
        LocalDate date,
        BigDecimal predictedBalance,
        String reason
    ) {}
}
