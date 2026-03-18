package com.flowguard.dto;

import com.flowguard.domain.BudgetCategoryEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetCategoryDto(
        UUID id,
        int periodYear,
        int periodMonth,
        String category,
        BigDecimal budgetedAmount
) {
    public static BudgetCategoryDto from(BudgetCategoryEntity e) {
        return new BudgetCategoryDto(e.getId(), e.getPeriodYear(), e.getPeriodMonth(),
                e.getCategory(), e.getBudgetedAmount());
    }
}
