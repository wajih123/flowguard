package com.flowguard.service;

import com.flowguard.domain.AlertEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.BudgetVsActualDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The "ma femme" service — proactively coaches the user on spending behaviour.
 *
 * Generates alert candidates (no persistence — AlertService handles that):
 *
 * 1. BUDGET_RISK  — a category is ≥80% of its monthly budget with ≥7 days left
 * 2. SAVINGS_OPPORTUNITY — a spending category is 20%+ above its 3-month average
 *
 * Does NOT inject AlertService to avoid circular dependency.
 */
@ApplicationScoped
public class SpendingCoachService {

    @Inject BudgetService          budgetService;
    @Inject TransactionRepository  transactionRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    public List<SpendingPatternService.AlertCandidate> generateCoachAlerts(UserEntity user) {
        List<SpendingPatternService.AlertCandidate> candidates = new ArrayList<>();

        YearMonth now = YearMonth.now();
        candidates.addAll(budgetRiskAlerts(user.getId(), now));
        candidates.addAll(savingsOpportunityAlerts(user.getId(), now));

        return candidates;
    }

    // ── Budget risk ───────────────────────────────────────────────────────────

    /**
     * Fires BUDGET_RISK when a category has consumed ≥80% of its budget
     * and there are still ≥7 days left in the month.
     */
    private List<SpendingPatternService.AlertCandidate> budgetRiskAlerts(UUID userId, YearMonth period) {
        int daysLeft = period.atEndOfMonth().getDayOfMonth() - LocalDate.now().getDayOfMonth();
        if (daysLeft < 7) return List.of(); // Too close to month-end — not actionable

        BudgetVsActualDto report = budgetService.getBudgetVsActual(userId, period.getYear(), period.getMonthValue());
        List<SpendingPatternService.AlertCandidate> result = new ArrayList<>();

        for (BudgetVsActualDto.CategoryLine line : report.lines()) {
            if (line.budgeted().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal pct = line.actual()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(line.budgeted(), 1, RoundingMode.HALF_UP);

            if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
                // Already over budget
                result.add(new SpendingPatternService.AlertCandidate(
                        AlertEntity.AlertType.BUDGET_RISK,
                        AlertEntity.Severity.HIGH,
                        String.format(
                                "🚨 Budget \"%s\" dépassé : %s dépensés sur %s prévu (%.0f%%). Il reste %d jours — freinez !",
                                categoryLabel(line.category()),
                                fmt(line.actual()), fmt(line.budgeted()),
                                pct.doubleValue(), daysLeft)));
            } else if (pct.compareTo(BigDecimal.valueOf(80)) >= 0) {
                result.add(new SpendingPatternService.AlertCandidate(
                        AlertEntity.AlertType.BUDGET_RISK,
                        AlertEntity.Severity.MEDIUM,
                        String.format(
                                "⚠️ Budget \"%s\" à %.0f%% : %s sur %s utilisés, il reste %d jours et encore %s de marge. Surveillez vos dépenses.",
                                categoryLabel(line.category()),
                                pct.doubleValue(),
                                fmt(line.actual()), fmt(line.budgeted()),
                                daysLeft, fmt(line.budgeted().subtract(line.actual())))));
            }
        }
        return result;
    }

    // ── Savings opportunity ───────────────────────────────────────────────────

    /**
     * Compares this month's spend per category to the 3-month rolling average.
     * If ≥20% above average → SAVINGS_OPPORTUNITY alert with a concrete saving amount.
     */
    private List<SpendingPatternService.AlertCandidate> savingsOpportunityAlerts(UUID userId, YearMonth current) {
        // Current month spend by category
        Map<String, BigDecimal> currentMonth = spendByCategory(userId,
                current.atDay(1), LocalDate.now());

        // Previous 3 months spend by category
        Map<String, BigDecimal> prev3 = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = current.minusMonths(i);
            spendByCategory(userId, ym.atDay(1), ym.atEndOfMonth())
                    .forEach((cat, amt) -> prev3.merge(cat, amt, BigDecimal::add));
        }

        List<SpendingPatternService.AlertCandidate> result = new ArrayList<>();
        Set<String> excluded = Set.of("SALAIRE", "VIREMENT", "REMBOURSEMENT", "AUTRE");

        for (Map.Entry<String, BigDecimal> entry : currentMonth.entrySet()) {
            String cat = entry.getKey();
            if (excluded.contains(cat)) continue;

            BigDecimal thisMonth = entry.getValue();
            if (thisMonth.compareTo(BigDecimal.valueOf(30)) < 0) continue; // ignore trivial amounts

            BigDecimal prevTotal = prev3.getOrDefault(cat, BigDecimal.ZERO);
            if (prevTotal.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal prevMonthAvg = prevTotal.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            if (prevMonthAvg.compareTo(BigDecimal.ZERO) <= 0) continue;

            double overRatio = thisMonth.divide(prevMonthAvg, 4, RoundingMode.HALF_UP).doubleValue() - 1.0;
            if (overRatio < 0.20) continue;

            BigDecimal excess = thisMonth.subtract(prevMonthAvg).setScale(0, RoundingMode.HALF_UP);
            result.add(new SpendingPatternService.AlertCandidate(
                    AlertEntity.AlertType.SAVINGS_OPPORTUNITY,
                    AlertEntity.Severity.LOW,
                    String.format(
                            "💡 Ce mois-ci vous avez dépensé %s en \"%s\", soit %s de plus que votre moyenne des 3 derniers mois (%s/mois). Revenir à la normale vous économiserait %s.",
                            fmt(thisMonth), categoryLabel(cat),
                            fmt(excess), fmt(prevMonthAvg), fmt(excess))));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, BigDecimal> spendByCategory(UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.findByUserIdAndDateBetween(userId, from, to)
                .stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT
                        && !t.isInternal()
                        && t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString() + " €";
    }

    private static String categoryLabel(String cat) {
        return switch (cat) {
            case "RESTAURATION" -> "Restauration";
            case "SHOPPING"     -> "Shopping";
            case "LOISIRS"      -> "Loisirs";
            case "TRANSPORT"    -> "Transport";
            case "ABONNEMENT"   -> "Abonnements";
            case "SANTE"        -> "Santé";
            case "EDUCATION"    -> "Éducation";
            case "LOGEMENT"     -> "Logement";
            case "ALIMENTATION" -> "Alimentation";
            default -> cat.charAt(0) + cat.substring(1).toLowerCase().replace('_', ' ');
        };
    }
}
