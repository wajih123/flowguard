package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aggregate KPI snapshot for the admin dashboard.
 */
public record AdminKpiDto(
    long   totalUsers,
    long   activeUsers,
    long   pendingKyc,
    long   approvedKyc,
    long   rejectedKyc,
    long   totalCredits,
    long   pendingCredits,
    long   activeCredits,
    long   overdueCredits,
    BigDecimal totalCreditVolume,
    BigDecimal totalCreditFees,
    long   criticalAlerts,
    long   unreadAlerts,
    double mlModelAccuracy,
    Instant generatedAt
) {}
