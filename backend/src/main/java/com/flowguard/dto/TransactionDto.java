package com.flowguard.dto;

import com.flowguard.domain.TransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionDto(
    UUID id,
    UUID accountId,
    BigDecimal amount,
    String type,
    String label,
    String category,
    LocalDate date,           // serialised as "date" — matches frontend Transaction.date
    boolean isRecurring
) {
    public static TransactionDto from(TransactionEntity entity) {
        return new TransactionDto(
            entity.getId(),
            entity.getAccount().getId(),
            entity.getAmount(),
            entity.getType().name(),
            entity.getLabel(),
            entity.getCategory().name(),
            entity.getDate(),
            entity.isRecurring()
        );
    }
}
