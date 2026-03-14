package com.flowguard.dto;

import com.flowguard.domain.AccountEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO matching the frontend {@code BankAccount} interface.
 * Returned by {@code GET /api/banking/accounts}.
 */
public record BankAccountDto(
        UUID id,
        String bankName,
        String accountName,
        String ibanMasked,
        BigDecimal balance,
        String currency,r
        String accountType,
        String syncStatus,
        String lastSyncAt) {

    /** Masks all but the first 4 and last 4 characters of an IBAN. */
    private static String maskIban(String iban) {
        if (iban == null || iban.isBlank()) return null;
        if (iban.length() <= 8) return iban;
        return iban.substring(0, 4) + " **** **** **** " + iban.substring(iban.length() - 4);
    }

    public static BankAccountDto from(AccountEntity e) {
        return new BankAccountDto(
                e.getId(),
                e.getBankName() != null ? e.getBankName() : "",
                e.getAccountName() != null ? e.getAccountName() : e.getBankName(),
                maskIban(e.getIban()),
                e.getBalance(),
                e.getCurrency(),
                e.getAccountType() != null ? e.getAccountType() : "Checking Account",
                e.getSyncStatus() != null ? e.getSyncStatus().name() : "OK",
                e.getLastSyncAt() != null ? e.getLastSyncAt().toString() : null);
    }
}
