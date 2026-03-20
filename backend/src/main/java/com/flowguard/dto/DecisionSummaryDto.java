package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DecisionSummaryDto(
        String snapshotId,
        String computedAt,
        String riskLevel,
        int runwayDays,
        BigDecimal currentBalance,
        BigDecimal minProjectedBalance,
        LocalDate minProjectedDate,
        boolean deficitPredicted,
        double volatilityScore,
        List<CashDriverDto> drivers,
        List<CashActionDto> actions
) {}
