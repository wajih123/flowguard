package com.flowguard.service;

import com.flowguard.domain.TransactionEntity.TransactionCategory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Maps Bridge API category IDs to FlowGuard {@link TransactionCategory} values.
 *
 * <p>Bridge returns an integer {@code category_id} on each transaction.
 * This mapper translates the full Bridge taxonomy (100+ categories) to
 * FlowGuard's domain categories.
 *
 * @see <a href="https://docs.bridgeapi.io/docs/categories">Bridge categories reference</a>
 */
@ApplicationScoped
public class CategoryMapper {

    /**
     * Complete mapping: Bridge category_id → FlowGuard TransactionCategory.
     *
     * <p>Maps to existing TransactionCategory values:
     * ALIMENTATION, TRANSPORT, SALAIRE, LOYER, CHARGES_FISCALES, AUTRE,
     * ABONNEMENT, VIREMENT, ASSURANCE, FOURNISSEUR, CLIENT_PAYMENT
     */
    private static final Map<Integer, TransactionCategory> BRIDGE_TO_FLOWGUARD = Map.ofEntries(
        // ── Food & Groceries ──────────────────────────────────────────────────
        Map.entry(1,  TransactionCategory.ALIMENTATION),
        Map.entry(2,  TransactionCategory.ALIMENTATION),
        Map.entry(3,  TransactionCategory.ALIMENTATION),
        Map.entry(4,  TransactionCategory.ALIMENTATION),

        // ── Transport ────────────────────────────────────────────────────────
        Map.entry(5,  TransactionCategory.TRANSPORT),
        Map.entry(6,  TransactionCategory.TRANSPORT),
        Map.entry(7,  TransactionCategory.TRANSPORT),
        Map.entry(8,  TransactionCategory.TRANSPORT),

        // ── Income ───────────────────────────────────────────────────────────
        Map.entry(9,  TransactionCategory.SALAIRE),
        Map.entry(10, TransactionCategory.CLIENT_PAYMENT),
        Map.entry(11, TransactionCategory.CLIENT_PAYMENT),
        Map.entry(12, TransactionCategory.SALAIRE),
        Map.entry(13, TransactionCategory.VIREMENT),

        // ── Housing ──────────────────────────────────────────────────────────
        Map.entry(14, TransactionCategory.LOYER),
        Map.entry(15, TransactionCategory.LOYER),
        Map.entry(16, TransactionCategory.LOYER),
        Map.entry(17, TransactionCategory.ENERGIE),
        Map.entry(18, TransactionCategory.ENERGIE),
        Map.entry(19, TransactionCategory.ASSURANCE),

        // ── Taxes & Social Charges ───────────────────────────────────────────
        Map.entry(20, TransactionCategory.CHARGES_FISCALES),
        Map.entry(21, TransactionCategory.CHARGES_FISCALES),
        Map.entry(22, TransactionCategory.CHARGES_FISCALES),
        Map.entry(23, TransactionCategory.CHARGES_FISCALES),
        Map.entry(24, TransactionCategory.CHARGES_FISCALES),
        Map.entry(25, TransactionCategory.CHARGES_FISCALES),

        // ── Healthcare ───────────────────────────────────────────────────────
        Map.entry(26, TransactionCategory.AUTRE),
        Map.entry(27, TransactionCategory.AUTRE),
        Map.entry(28, TransactionCategory.AUTRE),
        Map.entry(29, TransactionCategory.AUTRE),
        Map.entry(30, TransactionCategory.ASSURANCE),

        // ── Shopping ─────────────────────────────────────────────────────────
        Map.entry(31, TransactionCategory.AUTRE),
        Map.entry(32, TransactionCategory.AUTRE),
        Map.entry(33, TransactionCategory.AUTRE),
        Map.entry(34, TransactionCategory.AUTRE),
        Map.entry(35, TransactionCategory.AUTRE),
        Map.entry(36, TransactionCategory.AUTRE),
        Map.entry(37, TransactionCategory.AUTRE),
        Map.entry(38, TransactionCategory.AUTRE),

        // ── Subscriptions ────────────────────────────────────────────────────
        Map.entry(39, TransactionCategory.ABONNEMENT),
        Map.entry(40, TransactionCategory.ABONNEMENT),
        Map.entry(41, TransactionCategory.ABONNEMENT),
        Map.entry(42, TransactionCategory.TELECOM),
        Map.entry(43, TransactionCategory.ABONNEMENT),
        Map.entry(44, TransactionCategory.ABONNEMENT),
        Map.entry(45, TransactionCategory.ABONNEMENT),

        // ── Transfers & Finance ───────────────────────────────────────────────
        Map.entry(60, TransactionCategory.VIREMENT),
        Map.entry(61, TransactionCategory.VIREMENT),
        Map.entry(62, TransactionCategory.VIREMENT),
        Map.entry(63, TransactionCategory.VIREMENT),
        Map.entry(64, TransactionCategory.VIREMENT),
        Map.entry(65, TransactionCategory.VIREMENT),
        Map.entry(66, TransactionCategory.VIREMENT),
        Map.entry(67, TransactionCategory.VIREMENT),

        // ── Insurance ────────────────────────────────────────────────────────
        Map.entry(46, TransactionCategory.ASSURANCE),
        Map.entry(47, TransactionCategory.ASSURANCE),
        Map.entry(48, TransactionCategory.ASSURANCE),
        Map.entry(49, TransactionCategory.ASSURANCE),

        // ── Professional / B2B ───────────────────────────────────────────────
        Map.entry(70, TransactionCategory.FOURNISSEUR),
        Map.entry(71, TransactionCategory.FOURNISSEUR),
        Map.entry(72, TransactionCategory.SALAIRE),          // salaires versés
        Map.entry(73, TransactionCategory.CHARGES_FISCALES), // charges patronales
        Map.entry(74, TransactionCategory.AUTRE),
        Map.entry(75, TransactionCategory.AUTRE),
        Map.entry(76, TransactionCategory.FOURNISSEUR),
        Map.entry(77, TransactionCategory.LOYER),
        Map.entry(78, TransactionCategory.AUTRE),
        Map.entry(79, TransactionCategory.AUTRE),            // publicité
        Map.entry(80, TransactionCategory.ABONNEMENT)        // outils pro
    );

    /**
     * Maps a Bridge category_id to a FlowGuard category.
     *
     * @param bridgeCategoryId the category_id from Bridge API
     * @return the matching FlowGuard category, or {@link TransactionCategory#OTHER} if unknown
     */
    public TransactionCategory map(Integer bridgeCategoryId) {
        if (bridgeCategoryId == null) return TransactionCategory.AUTRE;
        return BRIDGE_TO_FLOWGUARD.getOrDefault(bridgeCategoryId, TransactionCategory.AUTRE);
    }

    /**
     * Returns true if the category typically represents an income transaction.
     */
    public boolean isIncome(Integer bridgeCategoryId) {
        if (bridgeCategoryId == null) return false;
        return bridgeCategoryId >= 9 && bridgeCategoryId <= 13;
    }

    /**
     * Returns true if the transaction is a tax/social charge (URSSAF, TVA, IS).
     */
    public boolean isTaxOrSocialCharge(Integer bridgeCategoryId) {
        if (bridgeCategoryId == null) return false;
        return bridgeCategoryId >= 20 && bridgeCategoryId <= 25;
    }
}
