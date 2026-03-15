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
     */
    private static final Map<Integer, TransactionCategory> BRIDGE_TO_FLOWGUARD = Map.ofEntries(
        // ── Food & Groceries ──────────────────────────────────────────────────
        Map.entry(1,  TransactionCategory.FOOD),         // Alimentation & Dépicerie
        Map.entry(2,  TransactionCategory.FOOD),         // Restaurants & Cafés
        Map.entry(3,  TransactionCategory.FOOD),         // Bars & Boissons
        Map.entry(4,  TransactionCategory.FOOD),         // Livraison repas

        // ── Transport ────────────────────────────────────────────────────────
        Map.entry(5,  TransactionCategory.TRANSPORT),    // Carburant
        Map.entry(6,  TransactionCategory.TRANSPORT),    // Transports en commun
        Map.entry(7,  TransactionCategory.TRANSPORT),    // Taxis & VTC
        Map.entry(8,  TransactionCategory.TRANSPORT),    // Parking & Péages

        // ── Income ───────────────────────────────────────────────────────────
        Map.entry(9,  TransactionCategory.INCOME),       // Revenus salaires
        Map.entry(10, TransactionCategory.INCOME),       // Revenus autres / freelance
        Map.entry(11, TransactionCategory.INCOME),       // Dividendes
        Map.entry(12, TransactionCategory.INCOME),       // Indemnités & Allocations
        Map.entry(13, TransactionCategory.INCOME),       // Remboursements reçus

        // ── Housing ──────────────────────────────────────────────────────────
        Map.entry(14, TransactionCategory.HOUSING),      // Loyer
        Map.entry(15, TransactionCategory.HOUSING),      // Charges & Copropriété
        Map.entry(16, TransactionCategory.HOUSING),      // Travaux & Réparations
        Map.entry(17, TransactionCategory.HOUSING),      // Électricité & Gaz
        Map.entry(18, TransactionCategory.HOUSING),      // Eau
        Map.entry(19, TransactionCategory.HOUSING),      // Assurance habitation

        // ── Taxes & Social Charges ───────────────────────────────────────────
        Map.entry(20, TransactionCategory.TAXES),        // Cotisations sociales (URSSAF)
        Map.entry(21, TransactionCategory.TAXES),        // TVA
        Map.entry(22, TransactionCategory.TAXES),        // CFE / CVAE
        Map.entry(23, TransactionCategory.TAXES),        // IS / IR
        Map.entry(24, TransactionCategory.TAXES),        // Taxe foncière / d'habitation
        Map.entry(25, TransactionCategory.TAXES),        // Impôts divers

        // ── Healthcare ───────────────────────────────────────────────────────
        Map.entry(26, TransactionCategory.HEALTH),       // Médecin généraliste
        Map.entry(27, TransactionCategory.HEALTH),       // Pharmacie
        Map.entry(28, TransactionCategory.HEALTH),       // Hospitalisation
        Map.entry(29, TransactionCategory.HEALTH),       // Optique & Dentaire
        Map.entry(30, TransactionCategory.HEALTH),       // Mutuelle / Assurance santé

        // ── Shopping ─────────────────────────────────────────────────────────
        Map.entry(31, TransactionCategory.SHOPPING),     // Vêtements
        Map.entry(32, TransactionCategory.SHOPPING),     // Électronique
        Map.entry(33, TransactionCategory.SHOPPING),     // Maison & Jardin
        Map.entry(34, TransactionCategory.SHOPPING),     // Amazon & Marketplace
        Map.entry(35, TransactionCategory.SHOPPING),     // Jeux & Jouets
        Map.entry(36, TransactionCategory.SHOPPING),     // Sport & Loisirs
        Map.entry(37, TransactionCategory.SHOPPING),     // Produits de beauté
        Map.entry(38, TransactionCategory.SHOPPING),     // Librairie & Papeterie

        // ── Subscriptions ────────────────────────────────────────────────────
        Map.entry(39, TransactionCategory.SUBSCRIPTION), // Streaming vidéo (Netflix, Canal+)
        Map.entry(40, TransactionCategory.SUBSCRIPTION), // Streaming musique
        Map.entry(41, TransactionCategory.SUBSCRIPTION), // Logiciels & SaaS
        Map.entry(42, TransactionCategory.SUBSCRIPTION), // Téléphone & Internet
        Map.entry(43, TransactionCategory.SUBSCRIPTION), // Presse en ligne
        Map.entry(44, TransactionCategory.SUBSCRIPTION), // Abonnements divers
        Map.entry(45, TransactionCategory.SUBSCRIPTION), // Gym & Sport en ligne

        // ── Transfers & Finance ───────────────────────────────────────────────
        Map.entry(60, TransactionCategory.TRANSFER),     // Virements
        Map.entry(61, TransactionCategory.TRANSFER),     // Prélèvements SEPA
        Map.entry(62, TransactionCategory.TRANSFER),     // Épargne
        Map.entry(63, TransactionCategory.TRANSFER),     // Investissements
        Map.entry(64, TransactionCategory.TRANSFER),     // Remboursement crédit immobilier
        Map.entry(65, TransactionCategory.TRANSFER),     // Remboursement prêt conso
        Map.entry(66, TransactionCategory.TRANSFER),     // Retrait DAB
        Map.entry(67, TransactionCategory.TRANSFER),     // Frais bancaires

        // ── Insurance ────────────────────────────────────────────────────────
        Map.entry(46, TransactionCategory.INSURANCE),    // Assurance auto
        Map.entry(47, TransactionCategory.INSURANCE),    // Assurance vie
        Map.entry(48, TransactionCategory.INSURANCE),    // Assurance emprunteur
        Map.entry(49, TransactionCategory.INSURANCE),    // Prévoyance

        // ── Professional / B2B ───────────────────────────────────────────────
        Map.entry(70, TransactionCategory.SUPPLIER),     // Fournisseurs & Achats pro
        Map.entry(71, TransactionCategory.SUPPLIER),     // Matériel professionnel
        Map.entry(72, TransactionCategory.SALARY_OUT),   // Salaires versés
        Map.entry(73, TransactionCategory.SALARY_OUT),   // Charges patronales
        Map.entry(74, TransactionCategory.OTHER),        // Frais de déplacement pro
        Map.entry(75, TransactionCategory.OTHER),        // Frais de représentation
        Map.entry(76, TransactionCategory.OTHER),        // Sous-traitance & Freelances
        Map.entry(77, TransactionCategory.OTHER),        // Loyer professionnel
        Map.entry(78, TransactionCategory.OTHER),        // Expert-comptable / Juridique
        Map.entry(79, TransactionCategory.MARKETING),    // Publicité & Marketing
        Map.entry(80, TransactionCategory.MARKETING)     // Abonnements outils pro
    );

    /**
     * Maps a Bridge category_id to a FlowGuard category.
     *
     * @param bridgeCategoryId the category_id from Bridge API
     * @return the matching FlowGuard category, or {@link TransactionCategory#OTHER} if unknown
     */
    public TransactionCategory map(Integer bridgeCategoryId) {
        if (bridgeCategoryId == null) return TransactionCategory.OTHER;
        return BRIDGE_TO_FLOWGUARD.getOrDefault(bridgeCategoryId, TransactionCategory.OTHER);
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
