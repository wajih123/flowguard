package com.flowguard.dto;

import com.flowguard.domain.AlertEntity;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AlertThresholdRequest(
        @NotNull String alertType,
        BigDecimal minAmount,
        boolean enabled,
        @NotNull String minSeverity
) {}
