package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single event on the Cash Flow Calendar — invoice due, expected charge,
 * or predicted recurring transaction.
 */
public record CashCalendarEventDto(
        LocalDate date,
        String type,    // "INVOICE_DUE" | "INVOICE_OVERDUE" | "RECURRING_CHARGE" | "RECURRING_INCOME"
        String label,
        BigDecimal amount,  // positive = inflow, negative = outflow
        String status,      // "PENDING" | "OVERDUE" | "PREDICTED"
        String clientName   // nullable — for invoice events
) {}
