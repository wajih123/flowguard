package com.flowguard.dto;

import com.flowguard.domain.ForecastAccuracyLogEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ForecastAccuracyDto(
        UUID id,
        LocalDate forecastDate,
        int horizonDays,
        BigDecimal predictedBalance,
        BigDecimal actualBalance,
        BigDecimal mae,
        /** Accuracy percentage (100 - relative error) — null if actualBalance not yet known. */
        Double accuracyPct,
        Instant recordedAt
) {
    public static ForecastAccuracyDto from(ForecastAccuracyLogEntity e) {
        Double accuracy = null;
        if (e.getActualBalance() != null && e.getMae() != null &&
                e.getPredictedBalance().compareTo(BigDecimal.ZERO) != 0) {
            double maeAbs = e.getMae().abs().doubleValue();
            double pred = e.getPredictedBalance().abs().doubleValue();
            accuracy = Math.max(0.0, 100.0 - (maeAbs / pred * 100.0));
        }
        return new ForecastAccuracyDto(
                e.getId(), e.getForecastDate(), e.getHorizonDays(),
                e.getPredictedBalance(), e.getActualBalance(), e.getMae(),
                accuracy, e.getRecordedAt()
        );
    }
}
