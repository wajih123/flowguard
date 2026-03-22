package com.flowguard.dto;

import java.math.BigDecimal;

/**
 * Tax Savings Vault — shows reserved amounts for TVA, URSSAF, IS
 * and the resulting "actually spendable" balance.
 */
public record TaxVaultDto(
        BigDecimal grossIncomeLast30Days,
        BigDecimal tvaReserve,
        BigDecimal urssafReserve,
        BigDecimal isReserve,
        BigDecimal totalReserved,
        BigDecimal currentBalance,
        BigDecimal spendableBalance,
        String businessType
) {}
