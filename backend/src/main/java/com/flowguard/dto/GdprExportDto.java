package com.flowguard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RGPD data export (art. 20 RGPD — droit à la portabilité).
 */
public record GdprExportDto(
    UserInfo user,
    List<AccountInfo> accounts,
    List<TransactionInfo> transactions,
    List<CreditInfo> credits,
    List<AlertInfo> alerts,
    Instant exportedAt
) {
    public record UserInfo(UUID id, String firstName, String lastName, String email,
                           String companyName, String userType, String kycStatus,
                           Instant gdprConsentAt, Instant createdAt) {}

    public record AccountInfo(UUID id, String iban, String bic, String balance,
                              String currency, String bankName, String status) {}

    public record TransactionInfo(UUID id, UUID accountId, String amount, String type,
                                  String label, String category, String date, boolean recurring) {}

    public record CreditInfo(UUID id, String amount, String fee, String totalRepayment,
                             String taegPercent, String purpose, String status,
                             Instant dueDate, Instant createdAt) {}

    public record AlertInfo(UUID id, String type, String severity, String message,
                            boolean isRead, Instant createdAt) {}
}
