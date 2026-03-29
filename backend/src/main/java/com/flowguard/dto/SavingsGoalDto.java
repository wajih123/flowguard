package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalDto(
        UUID id,
        String goalType,
        String goalTypeLabel,
        String goalTypeEmoji,
        String label,
        BigDecimal targetAmount,
        BigDecimal currentBalance,
        BigDecimal progressPercent,
        LocalDate targetDate,
        BigDecimal monthlyContribution,
        BigDecimal recommendedMonthly,
        long estimatedDaysToReach,
        LocalDate estimatedDate,
        /** Human-readable coach tip, e.g. "Réduisez restauration de 50 €/mois pour atteindre cet objectif 3 mois plus tôt." */
        String coachTip
) {}
