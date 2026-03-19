package fr.flowguard.decision.service;

import fr.flowguard.decision.entity.CashDriverEntity;
import fr.flowguard.decision.entity.CashRecommendationEntity;
import fr.flowguard.decision.entity.CashRiskSnapshotEntity;
import fr.flowguard.invoice.entity.InvoiceEntity;
import fr.flowguard.payment.entity.PaymentInitiationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates context-aware cash improvement actions from detected drivers.
 *
 * Each recommendation is:
 *   - Tied to a specific driver (traceable)
 *   - Quantified in euros and time horizon
 *   - Given a confidence score (0–1)
 *
 * Action types:
 *   DELAY_SUPPLIER           – defer outgoing payment
 *   SEND_REMINDERS           – chase late invoices
 *   REDUCE_SPEND             – cut recurring costs
 *   REQUEST_CREDIT           – suggest credit line
 *   ACCELERATE_RECEIVABLES   – discount or factor outstanding invoices
 */
@ApplicationScoped
public class RecommendationEngineService {

    @Transactional
    public List<CashRecommendationEntity> generate(
            String userId,
            CashRiskSnapshotEntity snapshot,
            List<CashDriverEntity> drivers) {

        List<CashRecommendationEntity> results = new ArrayList<>();

        for (CashDriverEntity driver : drivers) {
            switch (driver.driverType) {
                case "LATE_INVOICE" -> {
                    // Only for overdue – suggest sending a reminder
                    if (driver.referenceId != null) {
                        InvoiceEntity inv = InvoiceEntity.findById(driver.referenceId);
                        if (inv != null && ("SENT".equals(inv.status) || "OVERDUE".equals(inv.status))) {
                            results.add(reco(userId, snapshot.id,
                                    "SEND_REMINDERS",
                                    String.format("Envoyer une relance à %s pour la facture %s – " +
                                            "encaissement attendu de %,.0f€ TTC sous 7 jours",
                                            inv.clientName, inv.number, inv.totalTtc.doubleValue()),
                                    inv.totalTtc, 7, 0.70));
                        }
                    }
                    if (driver.amount != null && driver.amount.compareTo(new BigDecimal("5000")) > 0) {
                        results.add(reco(userId, snapshot.id,
                                "ACCELERATE_RECEIVABLES",
                                String.format("Escompter ou afacturer la créance de %,.0f€ " +
                                        "pour obtenir des liquidités immédiates",
                                        driver.amount.doubleValue()),
                                driver.amount.multiply(new BigDecimal("0.95")), 3, 0.55));
                    }
                }
                case "SUPPLIER_PAYMENT" -> {
                    if (driver.referenceId != null) {
                        PaymentInitiationEntity p = PaymentInitiationEntity.findById(driver.referenceId);
                        if (p != null && ("PENDING".equals(p.status))) {
                            results.add(reco(userId, snapshot.id,
                                    "DELAY_SUPPLIER",
                                    String.format("Reporter de 5 jours le virement de %,.0f€ vers %s " +
                                            "pour préserver votre trésorerie à court terme",
                                            p.amount.doubleValue(), p.creditorName),
                                    p.amount, 5, 0.85));
                        }
                    }
                }
                case "RECURRING_COST" -> {
                    if (driver.amount != null) {
                        BigDecimal impact = driver.amount.divide(new BigDecimal("2"), 0, java.math.RoundingMode.HALF_UP);
                        results.add(reco(userId, snapshot.id,
                                "REDUCE_SPEND",
                                String.format("Renégocier ou suspendre la charge récurrente \"%s\" " +
                                        "→ économie estimée %,.0f€/mois",
                                        driver.label, impact.doubleValue()),
                                impact, 30, 0.60));
                    }
                }
                case "REVENUE_DROP" -> {
                    results.add(reco(userId, snapshot.id,
                            "REQUEST_CREDIT",
                            "Votre chiffre d'affaires a baissé ce mois. Envisagez une ligne de crédit " +
                                    "court terme ou une avance sur factures pour couvrir vos charges fixes.",
                            driver.amount != null ? driver.amount : BigDecimal.ZERO, 14, 0.50));
                }
                case "TAX_PAYMENT" -> {
                    if (driver.amount != null && snapshot.runwayDays != null && snapshot.runwayDays < 21) {
                        BigDecimal safeToDelay = driver.amount.multiply(new BigDecimal("0.30"));
                        results.add(reco(userId, snapshot.id,
                                "REQUEST_CREDIT",
                                String.format("Paiement %s de %,.0f€ dans %d jours. " +
                                        "Provisionnez ou demandez un échelonnement auprès de l'administration.",
                                        driver.label, driver.amount.doubleValue(),
                                        driver.impactDays != null ? driver.impactDays : 30),
                                safeToDelay, driver.impactDays != null ? driver.impactDays : 30, 0.75));
                    }
                }
                default -> { /* no recommendation for unknown driver types */ }
            }
        }

        // Global: if HIGH or CRITICAL and no recommendations yet, add generic credit suggestion
        if (results.isEmpty() && ("HIGH".equals(snapshot.riskLevel) || "CRITICAL".equals(snapshot.riskLevel))) {
            results.add(reco(userId, snapshot.id,
                    "REQUEST_CREDIT",
                    "Votre niveau de risque est élevé. Contactez votre conseiller bancaire " +
                            "pour une facilité de caisse ou un découvert autorisé.",
                    snapshot.currentBalance != null ? snapshot.currentBalance.multiply(new BigDecimal("0.20")) : BigDecimal.ZERO,
                    7, 0.45));
        }

        // Persist all
        for (CashRecommendationEntity r : results) {
            r.persist();
        }
        return results;
    }

    private CashRecommendationEntity reco(String userId, String snapshotId,
                                          String actionType, String description,
                                          BigDecimal impact, int horizonDays, double confidence) {
        CashRecommendationEntity r = new CashRecommendationEntity();
        r.userId = userId;
        r.snapshotId = snapshotId;
        r.actionType = actionType;
        r.description = description;
        r.estimatedImpact = impact.setScale(2, java.math.RoundingMode.HALF_UP);
        r.horizonDays = horizonDays;
        r.confidence = BigDecimal.valueOf(confidence).setScale(3, java.math.RoundingMode.HALF_UP);
        r.status = "PENDING";
        return r;
    }
}