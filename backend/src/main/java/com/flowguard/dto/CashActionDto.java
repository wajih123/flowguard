package com.flowguard.dto;

import java.math.BigDecimal;

public record CashActionDto(
        String id,
        String actionType,
        String description,
        BigDecimal estimatedImpact,
        int horizonDays,
        double confidence,
        String status
) {}
