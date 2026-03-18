package com.flowguard.dto;

import com.flowguard.domain.TaxEstimateEntity;
import com.flowguard.domain.TaxEstimateEntity.TaxType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaxEstimateDto(
        UUID id,
        TaxType taxType,
        String periodLabel,
        BigDecimal estimatedAmount,
        LocalDate dueDate,
        Instant paidAt,
        /** Days until due (negative = overdue). */
        int daysUntilDue,
        boolean isPaid
) {
    public static TaxEstimateDto from(TaxEstimateEntity e) {
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), e.getDueDate());
        return new TaxEstimateDto(
                e.getId(), e.getTaxType(), e.getPeriodLabel(), e.getEstimatedAmount(),
                e.getDueDate(), e.getPaidAt(), days, e.getPaidAt() != null
        );
    }
}
