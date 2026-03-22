package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CashGoalDto(
        UUID id,
        BigDecimal targetAmount,
        String label,
        BigDecimal currentBalance,
        BigDecimal progressPercent,  // 0–100
        long estimatedDaysToReach,   // -1 if not calculable
        LocalDate estimatedDate
) {}
