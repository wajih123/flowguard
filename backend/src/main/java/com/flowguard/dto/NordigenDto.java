package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NordigenInstitutionDto(
    String id,
    String name,
    String logoUrl,
    int transactionTotalDays
) {}

public record NordigenRequisitionDto(
    String requisitionId,
    String connectionUrl
) {}

public record NordigenTokens(
    String accessToken,
    String refreshToken,
    int expiresIn
) {}

public record NordigenTxDto(
    String externalId,
    BigDecimal amount,
    String type,
    String label,
    String creditorName,
    String debtorName,
    LocalDate bookingDate,
    String status
) {}

public enum TransactionCategory {
    LOYER, SALAIRE, ALIMENTATION, TRANSPORT, ABONNEMENT, ENERGIE, TELECOM,
    ASSURANCE, CHARGES_FISCALES, CLIENT_PAYMENT, AUTRE
}
