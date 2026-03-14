package com.flowguard.dto;

import com.flowguard.domain.AlertEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AlertDto(
    UUID id,
    AlertEntity.AlertType type,
    AlertEntity.Severity severity,
    String message,
    BigDecimal projectedDeficit,
    LocalDate triggerDate,
    boolean isRead,
    Instant createdAt
) {
    public static AlertDto from(AlertEntity entity) {
        return new AlertDto(
            entity.getId(),
            entity.getType(),
            entity.getSeverity(),
            entity.getMessage(),
            entity.getProjectedDeficit(),
            entity.getTriggerDate(),
            entity.isRead(),
            entity.getCreatedAt()
        );
    }
}
