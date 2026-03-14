package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.FlashCreditEntity;
import com.flowguard.repository.FlashCreditRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Job schedulé qui gère :
 * 1. Le passage en OVERDUE des crédits impayés à échéance
 * 2. La génération d'alertes de relance (PAYMENT_DUE)
 *
 * Exécuté toutes les heures.
 */
@ApplicationScoped
public class CreditOverdueService {

    private static final Logger LOG = Logger.getLogger(CreditOverdueService.class);

    @Inject
    FlashCreditRepository flashCreditRepository;

    @Inject
    AlertService alertService;

    @Scheduled(cron = "0 0 * * * ?") // Every hour
    @Transactional
    void checkOverdueCredits() {
        LOG.info("Running overdue credit check...");

        Instant now = Instant.now();
        List<FlashCreditEntity> overdueCandidates = flashCreditRepository.findOverdueCandidates(now);

        for (FlashCreditEntity credit : overdueCandidates) {
            credit.setStatus(FlashCreditEntity.CreditStatus.OVERDUE);

            long daysOverdue = ChronoUnit.DAYS.between(credit.getDueDate(), now);

            AlertEntity.Severity severity;
            if (daysOverdue > 30) {
                severity = AlertEntity.Severity.CRITICAL;
            } else if (daysOverdue > 14) {
                severity = AlertEntity.Severity.HIGH;
            } else if (daysOverdue > 7) {
                severity = AlertEntity.Severity.MEDIUM;
            } else {
                severity = AlertEntity.Severity.LOW;
            }

            String message = String.format(
                    "Crédit flash de %s € impayé depuis %d jour(s). "
                    + "Montant à rembourser : %s €. Veuillez régulariser votre situation.",
                    credit.getAmount().toPlainString(),
                    daysOverdue,
                    credit.getTotalRepayment().toPlainString()
            );

            alertService.createAlert(
                    credit.getUser().getId(),
                    AlertEntity.AlertType.PAYMENT_DUE,
                    severity,
                    message,
                    credit.getTotalRepayment().negate(),
                    java.time.LocalDate.now()
            );

            LOG.infof("Credit %s marked OVERDUE (%d days), alert sent to user %s",
                    credit.getId(), daysOverdue, credit.getUser().getId());
        }

        if (!overdueCandidates.isEmpty()) {
            LOG.infof("Processed %d overdue credits", overdueCandidates.size());
        }
    }
}
