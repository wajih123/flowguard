package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionSummaryDto(
        String label,
        String category,
        BigDecimal monthlyAmount,
        LocalDate lastUsedDate,
        int monthsSinceLastUse,
        boolean isStale,
        int occurrencesLast12Months
) {}
