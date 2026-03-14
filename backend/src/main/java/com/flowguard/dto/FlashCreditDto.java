package com.flowguard.dto;

import com.flowguard.domain.FlashCreditEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlashCreditDto(
    UUID id,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal totalRepayment,
    BigDecimal taegPercent,
    String purpose,
    FlashCreditEntity.CreditStatus status,
    Instant dueDate,
    Instant retractionDeadline,
    boolean retractionExercised,
    Instant createdAt
) {
    public static FlashCreditDto from(FlashCreditEntity entity) {
        return new FlashCreditDto(
            entity.getId(),
            entity.getAmount(),
            entity.getFee(),
            entity.getTotalRepayment(),
            entity.getTaegPercent(),
            entity.getPurpose(),
            entity.getStatus(),
            entity.getDueDate(),
            entity.getRetractionDeadline(),
            entity.isRetractionExercised(),
            entity.getCreatedAt()
        );
    }
}
