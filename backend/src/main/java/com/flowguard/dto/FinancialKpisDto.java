package com.flowguard.dto;

import java.math.BigDecimal;

/**
 * KPIs financiers clés :
 * <ul>
 *   <li>BFR — Besoin en Fonds de Roulement</li>
 *   <li>DSO — Days Sales Outstanding (délai moyen de paiement clients)</li>
 *   <li>DPO — Days Payable Outstanding (délai moyen de paiement fournisseurs)</li>
 *   <li>Cash burn rate — vitesse de consommation de trésorerie</li>
 *   <li>Runway — nombre de jours restants au rythme actuel</li>
 * </ul>
 */
public record FinancialKpisDto(
        BigDecimal bfr,
        int dso,
        int dpo,
        BigDecimal monthlyBurnRate,
        BigDecimal dailyBurnRate,
        int runwayDays,
        BigDecimal totalIncome30d,
        BigDecimal totalExpenses30d,
        BigDecimal currentBalance
) {}
