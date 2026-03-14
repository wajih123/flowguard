package com.flowguard.dto;

import com.flowguard.domain.FlashCreditEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Flash Credit row for admin list/detail views.
 */
public record AdminCreditDto(
    UUID   id,
    UUID   userId,
    String userEmailMasked,
    String userFullName,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal totalRepayment,
    BigDecimal taegPercent,
    String purpose,
    FlashCreditEntity.CreditStatus status,
    Instant dueDate,
    Instant disbursedAt,
    Instant repaidAt,
    Instant retractionDeadline,
    boolean retractionExercised,
    Instant createdAt
) {
    public static AdminCreditDto from(FlashCreditEntity c) {
        var u = c.getUser();
        return new AdminCreditDto(
            c.getId(),
            u != null ? u.getId() : null,
            u != null ? AdminUserDto.maskEmail(u.getEmail()) : null,
            u != null ? u.getFirstName() + " " + u.getLastName() : null,
            c.getAmount(),
            c.getFee(),
            c.getTotalRepayment(),
            c.getTaegPercent(),
            c.getPurpose(),
            c.getStatus(),
            c.getDueDate(),
            c.getDisbursedAt(),
            c.getRepaidAt(),
            c.getRetractionDeadline(),
            c.isRetractionExercised(),
            c.getCreatedAt()
        );
    }
}
