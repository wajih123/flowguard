package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tax declaration preparation data.
 * Maps computed values to French tax form box codes so the user can
 * copy-paste them into impots.gouv.fr or hand them to their accountant.
 *
 * Supported regimes:
 *  - MICRO_BIC   : Micro-entreprise BIC (vente / hébergement)
 *  - MICRO_BNC   : Micro-entreprise BNC (prestations de services, freelance)
 *  - REEL_SIMPLIFIE : Régime réel simplifié (liasse 2033)
 *  - TVA_CA3     : Déclaration TVA mensuelle / trimestrielle (CA3)
 *  - TVA_CA12    : Déclaration TVA annuelle (CA12)
 */
public record TaxDeclarationDto(
        int     fiscalYear,
        String  regime,
        String  businessType,

        // ── Revenue ───────────────────────────────────────────────────────────
        BigDecimal grossRevenue,          // CA brut HT
        BigDecimal vatCollected,          // TVA collectée (20% x CA)
        BigDecimal netRevenue,            // CA net (après abattement micro ou charges réelles)

        // ── Micro-BNC / Micro-BIC specific ───────────────────────────────────
        BigDecimal abattement,            // 34% (BNC) or 50/71% (BIC)
        BigDecimal taxableIncome,         // Revenu imposable = CA - abattement

        // ── Charges (régime réel) ─────────────────────────────────────────────
        BigDecimal chargesLoyer,
        BigDecimal chargesTelecom,
        BigDecimal chargesAssurance,
        BigDecimal chargesTransport,
        BigDecimal chargesAbonnements,
        BigDecimal chargesEnergie,
        BigDecimal chargesFournisseurs,
        BigDecimal chargesAutres,
        BigDecimal totalCharges,          // sum of all charge categories
        BigDecimal beneficeNet,           // grossRevenue - totalCharges

        // ── TVA ───────────────────────────────────────────────────────────────
        BigDecimal tvaCollectee,          // line CA3 box 0979
        BigDecimal tvaDeductible,         // line CA3 box 0703 (estimated from DEBIT tx)
        BigDecimal tvaSolde,              // tvaCollectee - tvaDeductible

        // ── 2042-C Pro box codes (key=box code, value=amount) ─────────────────
        Map<String, BigDecimal> formBoxes,

        // ── Warnings for user ─────────────────────────────────────────────────
        List<String> warnings,

        // ── Invoice counts for reference ─────────────────────────────────────
        int totalInvoices,
        int paidInvoices,
        int uncategorizedTransactions
) {}
