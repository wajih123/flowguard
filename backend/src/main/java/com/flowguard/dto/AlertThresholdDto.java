package com.flowguard.dto;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.AlertThresholdEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record AlertThresholdDto(
        UUID id,
        String alertType,
        BigDecimal minAmount,
        boolean enabled,
        String minSeverity
) {
    public static AlertThresholdDto from(AlertThresholdEntity entity) {
        return new AlertThresholdDto(
                entity.getId(),
                entity.getAlertType().name(),
                entity.getMinAmount(),
                entity.isEnabled(),
                entity.getMinSeverity().name()
        );
    }
}
