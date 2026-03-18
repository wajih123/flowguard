package com.flowguard.dto;

import com.flowguard.domain.PaymentInitiationEntity;
import com.flowguard.domain.PaymentInitiationEntity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitiationDto(
        UUID id,
        String creditorName,
        String creditorIban,
        BigDecimal amount,
        String currency,
        String reference,
        PaymentStatus status,
        String swanPaymentId,
        Instant initiatedAt,
        Instant executedAt
) {
    public static PaymentInitiationDto from(PaymentInitiationEntity e) {
        return new PaymentInitiationDto(
                e.getId(), e.getCreditorName(), e.getCreditorIban(),
                e.getAmount(), e.getCurrency(), e.getReference(),
                e.getStatus(), e.getSwanPaymentId(), e.getInitiatedAt(), e.getExecutedAt()
        );
    }
}
