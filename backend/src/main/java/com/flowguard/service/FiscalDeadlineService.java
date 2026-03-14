package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.UserRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Service d'alertes pour les échéances fiscales et sociales françaises :
 * <ul>
 *   <li>TVA mensuelle / trimestrielle</li>
 *   <li>URSSAF trimestrielle (micro/TNS) / mensuelle</li>
 *   <li>Acomptes IS (Corporate tax)</li>
 *   <li>CFE (Cotisation Foncière des Entreprises)</li>
 * </ul>
 *
 * Les alertes sont générées 7 jours avant chaque échéance.
 */
@ApplicationScoped
public class FiscalDeadlineService {

    private static final Logger LOG = Logger.getLogger(FiscalDeadlineService.class);
    private static final int ALERT_DAYS_BEFORE = 7;

    @Inject
    UserRepository userRepository;

    @Inject
    AlertService alertService;

    /**
     * Scheduled daily at 08:00 — checks upcoming fiscal deadlines and generates alerts.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    void checkFiscalDeadlines() {
        LOG.info("Running fiscal deadline check...");

        LocalDate today = LocalDate.now();
        LocalDate alertHorizon = today.plusDays(ALERT_DAYS_BEFORE);
        List<FiscalDeadline> upcomingDeadlines = getDeadlinesBetween(today, alertHorizon);

        if (upcomingDeadlines.isEmpty()) {
            return;
        }

        List<UserEntity> users = userRepository.listAll();
        for (UserEntity user : users) {
            for (FiscalDeadline deadline : upcomingDeadlines) {
                if (isDeadlineRelevant(user, deadline)) {
                    long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, deadline.date);
                    String message = String.format(
                            "%s — échéance le %s (dans %d jour%s). %s",
                            deadline.label,
                            deadline.date,
                            daysUntil,
                            daysUntil > 1 ? "s" : "",
                            deadline.description
                    );

                    alertService.createAlert(
                            user.getId(),
                            AlertEntity.AlertType.PAYMENT_DUE,
                            daysUntil <= 3 ? AlertEntity.Severity.HIGH : AlertEntity.Severity.MEDIUM,
                            message,
                            null,
                            deadline.date
                    );
                }
            }
        }
    }

    /**
     * Returns all fiscal deadlines falling within the given date range (inclusive).
     */
    List<FiscalDeadline> getDeadlinesBetween(LocalDate from, LocalDate to) {
        List<FiscalDeadline> all = computeDeadlinesForYear(from.getYear());
        if (from.getYear() != to.getYear()) {
            all.addAll(computeDeadlinesForYear(to.getYear()));
        }

        return all.stream()
                .filter(d -> !d.date.isBefore(from) && !d.date.isAfter(to))
                .toList();
    }

    // ---- Deadline calendar ----

    List<FiscalDeadline> computeDeadlinesForYear(int year) {
        List<FiscalDeadline> deadlines = new ArrayList<>();

        // TVA mensuelle : le 15~19 de chaque mois (simplified to 15th)
        for (Month month : Month.values()) {
            LocalDate tvaDate = adjustForWeekend(LocalDate.of(year, month, 15));
            deadlines.add(new FiscalDeadline(
                    tvaDate,
                    "TVA — Déclaration mensuelle (CA3)",
                    "Déclaration et paiement de la TVA du mois précédent.",
                    DeadlineCategory.TVA
            ));
        }

        // TVA trimestrielle : 15/04, 15/07, 15/10, 15/01 (N+1)
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 4, 15)),
                "TVA — Déclaration trimestrielle T1",
                "TVA trimestrielle (CA12 ou régime simplifié).",
                DeadlineCategory.TVA
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 7, 15)),
                "TVA — Déclaration trimestrielle T2",
                "TVA trimestrielle (CA12 ou régime simplifié).",
                DeadlineCategory.TVA
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 10, 15)),
                "TVA — Déclaration trimestrielle T3",
                "TVA trimestrielle (CA12 ou régime simplifié).",
                DeadlineCategory.TVA
        ));

        // URSSAF trimestrielle (micro-entrepreneurs) : 30/04, 31/07, 31/10, 31/01
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 4, 30)),
                "URSSAF — Cotisations T1",
                "Déclaration et paiement des cotisations sociales trimestrielles.",
                DeadlineCategory.URSSAF
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 7, 31)),
                "URSSAF — Cotisations T2",
                "Déclaration et paiement des cotisations sociales trimestrielles.",
                DeadlineCategory.URSSAF
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 10, 31)),
                "URSSAF — Cotisations T3",
                "Déclaration et paiement des cotisations sociales trimestrielles.",
                DeadlineCategory.URSSAF
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 1, 31)),
                "URSSAF — Cotisations T4 (année précédente)",
                "Déclaration et paiement des cotisations sociales trimestrielles.",
                DeadlineCategory.URSSAF
        ));

        // IS — Acomptes trimestriels : 15/03, 15/06, 15/09, 15/12
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 3, 15)),
                "IS — 1er acompte d'impôt sur les sociétés",
                "Premier acompte trimestriel (25% de l'IS de l'exercice précédent).",
                DeadlineCategory.IS
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 6, 15)),
                "IS — 2e acompte d'impôt sur les sociétés",
                "Deuxième acompte trimestriel.",
                DeadlineCategory.IS
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 9, 15)),
                "IS — 3e acompte d'impôt sur les sociétés",
                "Troisième acompte trimestriel.",
                DeadlineCategory.IS
        ));
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 12, 15)),
                "IS — 4e acompte d'impôt sur les sociétés",
                "Quatrième acompte trimestriel.",
                DeadlineCategory.IS
        ));

        // CFE — 15 décembre
        deadlines.add(new FiscalDeadline(
                adjustForWeekend(LocalDate.of(year, 12, 15)),
                "CFE — Cotisation foncière des entreprises",
                "Paiement du solde de la CFE. Un acompte de 50% est dû au 15 juin pour les redevables > 3 000 €.",
                DeadlineCategory.CFE
        ));

        return deadlines;
    }

    private boolean isDeadlineRelevant(UserEntity user, FiscalDeadline deadline) {
        // INDIVIDUAL users don't have fiscal deadlines
        if (user.getUserType() == UserEntity.UserType.INDIVIDUAL) {
            return false;
        }

        // IS (corporate tax) only relevant for TPE/PME/SME (société)
        if (deadline.category == DeadlineCategory.IS) {
            return user.getUserType() == UserEntity.UserType.TPE
                || user.getUserType() == UserEntity.UserType.PME
                || user.getUserType() == UserEntity.UserType.SME;
        }

        return true;
    }

    private LocalDate adjustForWeekend(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.plusDays(2);
            case SUNDAY -> date.plusDays(1);
            default -> date;
        };
    }

    // ---- Inner types ----

    record FiscalDeadline(LocalDate date, String label, String description, DeadlineCategory category) {}

    enum DeadlineCategory {
        TVA, URSSAF, IS, CFE
    }
}
