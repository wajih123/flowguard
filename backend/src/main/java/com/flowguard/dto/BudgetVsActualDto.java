package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget vs Actual report for one month.
 * actualAmount is derived from TransactionService spending aggregation.
 */
public record BudgetVsActualDto(
        int year,
        int month,
        List<CategoryLine> lines,
        BigDecimal totalBudgeted,
        BigDecimal totalActual,
        BigDecimal netVariance
) {
    public record CategoryLine(
            String category,
            BigDecimal budgeted,
            BigDecimal actual,
            BigDecimal variance,
            /** OVER_BUDGET, UNDER_BUDGET, ON_TRACK */
            String status
    ) {}
}
