package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import com.flowguard.repository.UserRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Détecte les dépenses inhabituelles en comparant les dépenses récentes (7 jours)
 * à la moyenne historique (90 jours) par catégorie.
 *
 * Déclenche une alerte UNUSUAL_SPEND si une catégorie dépasse 2× la moyenne.
 */
@ApplicationScoped
public class AnomalyDetectionService {

    private static final Logger LOG = Logger.getLogger(AnomalyDetectionService.class);

    /** Seuil de déclenchement : dépense > THRESHOLD_FACTOR × moyenne historique */
    private static final double THRESHOLD_FACTOR = 2.0;
    /** Minimum absolu (ignorer les petites catégories) */
    private static final BigDecimal MIN_ABSOLUTE = new BigDecimal("100");

    @Inject
    UserRepository userRepository;

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    AlertService alertService;

    @Scheduled(cron = "0 30 8 * * ?") // Daily at 08:30
    @Transactional
    void detectAnomalies() {
        LOG.info("Running anomaly detection...");

        List<UserEntity> users = userRepository.listAll();
        for (UserEntity user : users) {
            try {
                detectForUser(user);
            } catch (Exception e) {
                LOG.errorf(e, "Error detecting anomalies for user %s", user.getId());
            }
        }
    }

    void detectForUser(UserEntity user) {
        List<AccountEntity> accounts = accountRepository.findByUserId(user.getId());
        if (accounts.isEmpty()) return;

        LocalDate now = LocalDate.now();
        LocalDate sevenDaysAgo = now.minusDays(7);
        LocalDate ninetyDaysAgo = now.minusDays(90);

        // Aggregate debits by category over 90 days and over last 7 days
        Map<TransactionEntity.TransactionCategory, BigDecimal> total90d = new HashMap<>();
        Map<TransactionEntity.TransactionCategory, BigDecimal> total7d = new HashMap<>();

        for (AccountEntity account : accounts) {
            List<TransactionEntity> txns90d = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), ninetyDaysAgo, now)
                    .stream()
                    .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT)
                    .toList();

            for (TransactionEntity t : txns90d) {
                total90d.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
                if (!t.getDate().isBefore(sevenDaysAgo)) {
                    total7d.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
                }
            }
        }

        // Compare: weekly average over 90d vs actual last 7d
        // 90 days ≈ ~12.86 weeks → weekly average = total90d / 12.86
        BigDecimal weeks = new BigDecimal("12.86");

        for (var entry : total7d.entrySet()) {
            TransactionEntity.TransactionCategory category = entry.getKey();
            BigDecimal recentSpend = entry.getValue();

            BigDecimal historical = total90d.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal weeklyAvg = historical.divide(weeks, 2, RoundingMode.HALF_UP);

            if (weeklyAvg.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (recentSpend.compareTo(MIN_ABSOLUTE) < 0) continue;

            double ratio = recentSpend.doubleValue() / weeklyAvg.doubleValue();

            if (ratio >= THRESHOLD_FACTOR) {
                BigDecimal excess = recentSpend.subtract(weeklyAvg);
                String message = String.format(
                        "Dépense inhabituelle en %s : %s € cette semaine (moyenne hebdomadaire : %s €, +%.0f%%).",
                        formatCategory(category),
                        recentSpend.toPlainString(),
                        weeklyAvg.toPlainString(),
                        (ratio - 1) * 100
                );

                AlertEntity.Severity severity;
                if (ratio >= 5.0) {
                    severity = AlertEntity.Severity.HIGH;
                } else if (ratio >= 3.0) {
                    severity = AlertEntity.Severity.MEDIUM;
                } else {
                    severity = AlertEntity.Severity.LOW;
                }

                alertService.createAlert(
                        user.getId(),
                        AlertEntity.AlertType.UNUSUAL_SPEND,
                        severity,
                        message,
                        excess.negate(),
                        now
                );
            }
        }
    }

    private String formatCategory(TransactionEntity.TransactionCategory category) {
        return switch (category) {
            case LOYER -> "Loyer";
            case SALAIRE -> "Salaires";
            case ALIMENTATION -> "Alimentation";
            case TRANSPORT -> "Transport";
            case ABONNEMENT -> "Abonnements";
            case ENERGIE -> "Énergie";
            case TELECOM -> "Télécom";
            case ASSURANCE -> "Assurance";
            case CHARGES_FISCALES -> "Charges fiscales";
            case FOURNISSEUR -> "Fournisseur";
            case CLIENT_PAYMENT -> "Paiement client";
            case VIREMENT -> "Virements";
            case AUTRE -> "Autre";
        };
    }
}
