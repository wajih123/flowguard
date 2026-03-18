package com.flowguard.dto;

import com.flowguard.domain.InvoiceEntity;
import com.flowguard.domain.InvoiceEntity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceDto(
        UUID id,
        String clientName,
        String clientEmail,
        String number,
        BigDecimal amountHt,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal totalTtc,
        String currency,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        Instant paidAt,
        String notes,
        Instant createdAt,
        /** Days overdue (positive) or days until due (negative). Null if paid/cancelled. */
        Integer daysOverdue
) {
    public static InvoiceDto from(InvoiceEntity e) {
        Integer daysOverdue = null;
        if (e.getStatus() == InvoiceStatus.SENT || e.getStatus() == InvoiceStatus.OVERDUE) {
            daysOverdue = (int) java.time.temporal.ChronoUnit.DAYS.between(e.getDueDate(), LocalDate.now());
        }
        return new InvoiceDto(
                e.getId(), e.getClientName(), e.getClientEmail(), e.getNumber(),
                e.getAmountHt(), e.getVatRate(), e.getVatAmount(), e.getTotalTtc(),
                e.getCurrency(), e.getStatus(), e.getIssueDate(), e.getDueDate(),
                e.getPaidAt(), e.getNotes(), e.getCreatedAt(), daysOverdue
        );
    }
}
