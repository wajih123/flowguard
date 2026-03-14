package com.flowguard.dto;

import java.math.BigDecimal;

/**
 * Aggregate credit stats for admin dashboard widget.
 */
public record CreditStatsDto(
    long   total,
    long   pending,
    long   active,
    long   overdue,
    long   repaid,
    long   rejected,
    BigDecimal volumeTotal,
    BigDecimal volumePending,
    BigDecimal feesCollected
) {}
