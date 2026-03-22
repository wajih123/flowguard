package com.flowguard.dto;

import java.math.BigDecimal;

/**
 * Per-client statistics: payment behaviour, revenue concentration, and late-payment prediction.
 */
public record ClientStatsDto(
        String clientName,
        String clientEmail,
        int invoiceCount,
        int paidInvoiceCount,
        BigDecimal totalRevenue,
        BigDecimal outstandingAmount,
        double revenueShare,        // % of total user revenue (0.0–100.0)
        int avgPaymentDays,         // average days between issueDate and paidAt (paid invoices only)
        int predictedPaymentDays,   // predicted days until payment for current unpaid invoices
        boolean isConcentrationRisk // true if revenueShare > 40%
) {}
