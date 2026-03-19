package fr.flowguard.decision.service;

import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import fr.flowguard.decision.entity.CashDriverEntity;
import fr.flowguard.invoice.entity.InvoiceEntity;
import fr.flowguard.payment.entity.PaymentInitiationEntity;
import fr.flowguard.tax.entity.TaxEstimateEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic, rule-based driver detection.
 *
 * Detects the top 5 cash-flow drivers for a given user and snapshot.
 * All logic is traceable — no ML. Each driver references its source entity.
 *
 * Driver types:
 *   TAX_PAYMENT      – upcoming unpaid tax obligation
 *   LATE_INVOICE     – sent/overdue invoice with no payment
 *   RECURRING_COST   – high-frequency recurring debit
 *   SUPPLIER_PAYMENT – pending outgoing SEPA payment
 *   REVENUE_DROP     – > 20% MoM revenue decline
 */
@ApplicationScoped
public class DriverDetectionService {

    /** Horizon window for "upcoming" obligations, in days. */
    private static final int HORIZON_DAYS = 30;
    private static final int MAX_DRIVERS = 5;

    /**
     * Detect top drivers for userId and persist them under snapshotId.
     * Returns the list of persisted drivers (not yet committed — caller handles TX).
     */
    @Transactional
    public List<CashDriverEntity> detect(String userId, String snapshotId) {
        List<DriverCandidate> candidates = new ArrayList<>();

        // 1. Tax obligations due within HORIZON_DAYS
        for (TaxEstimateEntity tax : TaxEstimateEntity.findUpcomingByUser(userId)) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), tax.dueDate);
            if (days <= HORIZON_DAYS && days >= 0) {
                candidates.add(new DriverCandidate(
                        "TAX_PAYMENT",
                        String.format("Échéance %s de %,.0f€ dans %d jours",
                                tax.taxType, tax.estimatedAmount.doubleValue(), days),
                        tax.estimatedAmount, (int) days,
                        tax.dueDate, tax.id, "TAX"
                ));
            }
        }

        // 2. Overdue or late invoices (not yet paid)
        for (InvoiceEntity inv : InvoiceEntity.findByUser(userId)) {
            if ("PAID".equals(inv.status) || "CANCELLED".equals(inv.status) || "DRAFT".equals(inv.status)) continue;
            long overdueDays = ChronoUnit.DAYS.between(inv.dueDate, LocalDate.now());
            if (overdueDays > 0) {
                candidates.add(new DriverCandidate(
                        "LATE_INVOICE",
                        String.format("Facture %s – %s en retard de %d jours (%,.0f€ TTC)",
                                inv.number, inv.clientName, overdueDays, inv.totalTtc.doubleValue()),
                        inv.totalTtc, (int) overdueDays,
                        inv.dueDate, inv.id, "INVOICE"
                ));
            } else if (overdueDays >= -14) {
                // Due within next 14 days
                candidates.add(new DriverCandidate(
                        "LATE_INVOICE",
                        String.format("Facture %s – %s à encaisser dans %d jours (%,.0f€ TTC)",
                                inv.number, inv.clientName, -overdueDays, inv.totalTtc.doubleValue()),
                        inv.totalTtc, (int) Math.abs(overdueDays),
                        inv.dueDate, inv.id, "INVOICE"
                ));
            }
        }

        // 3. Pending SEPA payments
        for (PaymentInitiationEntity p : PaymentInitiationEntity.findByUser(userId)) {
            if ("PENDING".equals(p.status) || "SUBMITTED".equals(p.status)) {
                candidates.add(new DriverCandidate(
                        "SUPPLIER_PAYMENT",
                        String.format("Virement en attente vers %s – %,.0f€ (%s)",
                                p.creditorName, p.amount.doubleValue(), p.status),
                        p.amount, 3,
                        null, p.id, "PAYMENT"
                ));
            }
        }

        // 4. Recurring costs: transactions labeled recurring in last 60 days
        List<BankAccountEntity> accounts = BankAccountEntity.findActiveByUserId(userId);
        if (!accounts.isEmpty()) {
            String accountId = accounts.get(0).id;
            List<TransactionEntity> recent = TransactionEntity
                    .find("accountId = ?1 AND transactionDate >= ?2 AND isRecurring = true ORDER BY amount ASC",
                            accountId, LocalDate.now().minusDays(60))
                    .page(0, 20).list();
            // Group: if same label appears >= 2 times, it's truly recurring
            java.util.Map<String, BigDecimal> recurringTotals = new java.util.LinkedHashMap<>();
            java.util.Map<String, Long> recurringCounts = new java.util.LinkedHashMap<>();
            for (TransactionEntity tx : recent) {
                if (tx.amount != null && tx.amount.compareTo(BigDecimal.ZERO) < 0) {
                    String key = tx.label != null ? tx.label.trim() : "Inconnu";
                    recurringTotals.merge(key, tx.amount.abs(), BigDecimal::add);
                    recurringCounts.merge(key, 1L, Long::sum);
                }
            }
            for (var entry : recurringTotals.entrySet()) {
                if (recurringCounts.getOrDefault(entry.getKey(), 0L) >= 2
                        && entry.getValue().compareTo(new BigDecimal("500")) > 0) {
                    candidates.add(new DriverCandidate(
                            "RECURRING_COST",
                            String.format("Charge récurrente \"%s\" : %,.0f€ sur 60 jours",
                                    entry.getKey(), entry.getValue().doubleValue()),
                            entry.getValue(), 30, null, null, null
                    ));
                }
            }

            // 5. Revenue drop: compare last 30d vs previous 30d credits
            LocalDate today = LocalDate.now();
            BigDecimal rev30 = sumCredits(accountId, today.minusDays(30), today);
            BigDecimal revPrev30 = sumCredits(accountId, today.minusDays(60), today.minusDays(30));
            if (revPrev30.compareTo(BigDecimal.ZERO) > 0) {
                double drop = (revPrev30.subtract(rev30)).doubleValue() / revPrev30.doubleValue();
                if (drop > 0.20) {
                    candidates.add(new DriverCandidate(
                            "REVENUE_DROP",
                            String.format("Chiffre d'affaires en baisse de %.0f%% ce mois", drop * 100),
                            revPrev30.subtract(rev30), 14, null, null, null
                    ));
                }
            }
        }

        // Sort by amount DESC, limit to MAX_DRIVERS
        candidates.sort(Comparator.comparing((DriverCandidate c) -> c.amount() != null ? c.amount() : BigDecimal.ZERO).reversed());
        List<DriverCandidate> top = candidates.subList(0, Math.min(MAX_DRIVERS, candidates.size()));

        // Persist
        List<CashDriverEntity> entities = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            DriverCandidate c = top.get(i);
            CashDriverEntity d = new CashDriverEntity();
            d.snapshotId = snapshotId;
            d.userId = userId;
            d.driverType = c.type();
            d.label = c.label();
            d.amount = c.amount();
            d.impactDays = c.impactDays();
            d.dueDate = c.dueDate();
            d.referenceId = c.referenceId();
            d.referenceType = c.referenceType();
            d.rank = (short) (i + 1);
            d.persist();
            entities.add(d);
        }
        return entities;
    }

    private BigDecimal sumCredits(String accountId, LocalDate from, LocalDate to) {
        List<TransactionEntity> txs = TransactionEntity
                .find("accountId = ?1 AND transactionDate >= ?2 AND transactionDate <= ?3 AND amount > 0",
                        accountId, from, to)
                .list();
        return txs.stream()
                .map(t -> t.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record DriverCandidate(
            String type, String label, BigDecimal amount, int impactDays,
            LocalDate dueDate, String referenceId, String referenceType) {}
}