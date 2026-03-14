package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDto(
    UUID id,
    String iban,
    String bic,
    BigDecimal balance,
    String currency,
    String bankName,
    String status
) {
    public static AccountDto from(com.flowguard.domain.AccountEntity entity) {
        return new AccountDto(
            entity.getId(),
            entity.getIban(),
            entity.getBic(),
            entity.getBalance(),
            entity.getCurrency(),
            entity.getBankName(),
            entity.getStatus().name()
        );
    }
}
