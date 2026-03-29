package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.SavingsGoalEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.SavingsGoalDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.SavingsGoalRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for multi-goal savings feature.
 *
 * Responsibilities:
 * - CRUD on SavingsGoalEntity
 * - Computing progress, recommended monthly contribution, and estimated date
 * - Generating a personalised "coach tip" based on user's spending patterns
 */
@ApplicationScoped
public class SavingsGoalService {

    @Inject SavingsGoalRepository goalRepository;
    @Inject AccountRepository accountRepository;
    @Inject TransactionRepository transactionRepository;

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<SavingsGoalDto> listGoals(UUID userId) {
        BigDecimal balance = totalBalance(userId);
        int goalCount = goalRepository.findByUserId(userId).size();
        BigDecimal balancePerGoal = goalCount > 0
                ? balance.divide(BigDecimal.valueOf(goalCount), 2, RoundingMode.HALF_UP)
                : balance;

        return goalRepository.findByUserId(userId).stream()
                .map(g -> toDto(g, balancePerGoal))
                .toList();
    }

    public SavingsGoalDto getGoal(UUID goalId, UUID userId) {
        SavingsGoalEntity goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Objectif introuvable"));
        BigDecimal balance = totalBalance(userId);
        int goalCount = goalRepository.findByUserId(userId).size();
        BigDecimal balancePerGoal = goalCount > 0
                ? balance.divide(BigDecimal.valueOf(goalCount), 2, RoundingMode.HALF_UP)
                : balance;
        return toDto(goal, balancePerGoal);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public SavingsGoalDto create(UserEntity user, SavingsGoalEntity.GoalType goalType,
                                 String label, BigDecimal targetAmount,
                                 LocalDate targetDate, BigDecimal monthlyContribution) {
        SavingsGoalEntity entity = SavingsGoalEntity.builder()
                .user(user)
                .goalType(goalType)
                .label(label != null && !label.isBlank() ? label : goalType.label())
                .targetAmount(targetAmount)
                .targetDate(targetDate)
                .monthlyContribution(monthlyContribution)
                .build();
        goalRepository.persist(entity);
        BigDecimal balance = totalBalance(user.getId());
        int goalCount = goalRepository.findByUserId(user.getId()).size();
        BigDecimal balancePerGoal = goalCount > 0
                ? balance.divide(BigDecimal.valueOf(goalCount), 2, RoundingMode.HALF_UP)
                : balance;
        return toDto(entity, balancePerGoal);
    }

    @Transactional
    public SavingsGoalDto update(UUID goalId, UUID userId,
                                 SavingsGoalEntity.GoalType goalType, String label,
                                 BigDecimal targetAmount, LocalDate targetDate,
                                 BigDecimal monthlyContribution) {
        SavingsGoalEntity entity = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Objectif introuvable"));
        if (goalType != null) entity.setGoalType(goalType);
        if (label != null && !label.isBlank()) entity.setLabel(label);
        if (targetAmount != null) entity.setTargetAmount(targetAmount);
        entity.setTargetDate(targetDate);
        entity.setMonthlyContribution(monthlyContribution);
        goalRepository.persistAndFlush(entity);
        BigDecimal balance = totalBalance(userId);
        int goalCount = goalRepository.findByUserId(userId).size();
        BigDecimal balancePerGoal = goalCount > 0
                ? balance.divide(BigDecimal.valueOf(goalCount), 2, RoundingMode.HALF_UP)
                : balance;
        return toDto(entity, balancePerGoal);
    }

    @Transactional
    public void delete(UUID goalId, UUID userId) {
        goalRepository.deleteByIdAndUserId(goalId, userId);
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private SavingsGoalDto toDto(SavingsGoalEntity goal, BigDecimal currentBalance) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal progressPct = target.compareTo(BigDecimal.ZERO) > 0
                ? currentBalance.multiply(BigDecimal.valueOf(100))
                        .divide(target, 1, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal shortfall = target.subtract(currentBalance).max(BigDecimal.ZERO);
        BigDecimal recommendedMonthly = computeRecommendedMonthly(goal, shortfall);
        BigDecimal effective = goal.getMonthlyContribution() != null
                ? goal.getMonthlyContribution()
                : recommendedMonthly;

        long estimatedDays = -1;
        LocalDate estimatedDate = null;
        if (shortfall.compareTo(BigDecimal.ZERO) > 0 && effective.compareTo(BigDecimal.ZERO) > 0) {
            long months = shortfall.divide(effective, 0, RoundingMode.CEILING).longValue();
            estimatedDays = months * 30L;
            estimatedDate = LocalDate.now().plusDays(estimatedDays);
        } else if (shortfall.compareTo(BigDecimal.ZERO) <= 0) {
            estimatedDays = 0;
            estimatedDate = LocalDate.now();
        }

        String coachTip = buildCoachTip(goal, shortfall, effective, estimatedDate);

        return new SavingsGoalDto(
                goal.getId(),
                goal.getGoalType().name(),
                goal.getGoalType().label(),
                goal.getGoalType().emoji(),
                goal.getLabel(),
                target,
                currentBalance,
                progressPct,
                goal.getTargetDate(),
                goal.getMonthlyContribution(),
                recommendedMonthly,
                estimatedDays,
                estimatedDate,
                coachTip
        );
    }

    /**
     * Compute the recommended monthly contribution.
     * If a target date is set: amount / months remaining (min 1).
     * Otherwise: shortfall / 12 (1-year horizon heuristic).
     */
    private BigDecimal computeRecommendedMonthly(SavingsGoalEntity goal, BigDecimal shortfall) {
        if (shortfall.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        if (goal.getTargetDate() != null) {
            long monthsRemaining = ChronoUnit.MONTHS.between(
                    YearMonth.now(), YearMonth.from(goal.getTargetDate()));
            if (monthsRemaining < 1) monthsRemaining = 1;
            return shortfall.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.CEILING);
        }
        // Default: 12-month horizon
        return shortfall.divide(BigDecimal.valueOf(12), 2, RoundingMode.CEILING);
    }

    /**
     * Build a personalised coach tip:
     * - If behind on a target-date goal: urgency message
     * - If goal is reachable: encouragement + savings suggestion
     * - If goal already reached: congratulations
     */
    private String buildCoachTip(SavingsGoalEntity goal, BigDecimal shortfall,
                                  BigDecimal effectiveMonthly, LocalDate estimatedDate) {
        if (shortfall.compareTo(BigDecimal.ZERO) <= 0) {
            return goal.getGoalType().emoji() + " Bravo ! Objectif \"" + goal.getLabel() + "\" atteint. Pensez à en définir un nouveau.";
        }

        if (goal.getTargetDate() != null) {
            long monthsToTarget = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(goal.getTargetDate()));
            if (effectiveMonthly.compareTo(BigDecimal.ZERO) > 0 && estimatedDate != null
                    && estimatedDate.isAfter(goal.getTargetDate())) {
                BigDecimal neededMonthly = shortfall.divide(
                        BigDecimal.valueOf(Math.max(monthsToTarget, 1)), 2, RoundingMode.CEILING);
                BigDecimal extra = neededMonthly.subtract(effectiveMonthly);
                return String.format(
                        "⚠️ Au rythme actuel vous n'atteindrez pas \"%s\" avant %s. Il faut épargner %s/mois supplémentaires.",
                        goal.getLabel(), goal.getTargetDate(), fmt(extra));
            }
        }

        // Generic positive tip
        String savingsTip = spendingSuggestion(goal.getUser().getId(), effectiveMonthly);
        if (savingsTip != null) return savingsTip;

        if (estimatedDate != null) {
            return String.format("💡 Objectif \"%s\" : à %s/mois, vous l'atteindrez en %s.",
                    goal.getLabel(), fmt(effectiveMonthly),
                    estimatedDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                            + " " + estimatedDate.getYear());
        }
        return "💡 Continuez vos efforts — chaque euro épargné vous rapproche de l'objectif !";
    }

    /**
     * Looks at the user's last 3 months of debit transactions and suggests the
     * category where cutting spending could fund the savings goal fastest.
     * Returns null if no meaningful suggestion can be made.
     */
    private String spendingSuggestion(UUID userId, BigDecimal monthlyTarget) {
        LocalDate from = LocalDate.now().minusMonths(3);
        List<TransactionEntity> debits = transactionRepository
                .findByUserIdAndDateBetween(userId, from, LocalDate.now())
                .stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT && !t.isInternal())
                .toList();

        if (debits.isEmpty()) return null;

        // Avg spend per category per month over the past 3 months
        Map<String, BigDecimal> totalByCategory = debits.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));

        // Look for the largest discretionary category (exclude SALAIRE, VIREMENT, REMBOURSEMENT)
        Set<String> excluded = Set.of("SALAIRE", "VIREMENT", "REMBOURSEMENT", "AUTRE");
        Optional<Map.Entry<String, BigDecimal>> topEntry = totalByCategory.entrySet().stream()
                .filter(e -> !excluded.contains(e.getKey()))
                .max(Map.Entry.comparingByValue());

        if (topEntry.isEmpty()) return null;

        BigDecimal avgMonthly = topEntry.getValue()
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal suggested = avgMonthly.multiply(new BigDecimal("0.15")).setScale(0, RoundingMode.HALF_UP);
        if (suggested.compareTo(BigDecimal.TEN) < 0) return null;

        String catLabel = categoryLabel(topEntry.getKey());
        return String.format(
                "💡 Réduire \"%s\" de %s/mois (-15%%) vous ferait économiser %s de plus vers cet objectif.",
                catLabel, fmt(suggested), fmt(suggested));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal totalBalance(UUID userId) {
        return accountRepository.findActiveByUserId(userId).stream()
                .map(AccountEntity::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString() + " €";
    }

    private static String categoryLabel(String cat) {
        return switch (cat) {
            case "RESTAURATION"     -> "Restauration";
            case "SHOPPING"         -> "Shopping";
            case "LOISIRS"          -> "Loisirs";
            case "TRANSPORT"        -> "Transport";
            case "ABONNEMENT"       -> "Abonnements";
            case "SANTE"            -> "Santé";
            case "EDUCATION"        -> "Éducation";
            case "LOGEMENT"         -> "Logement";
            default                 -> cat.charAt(0) + cat.substring(1).toLowerCase().replace('_', ' ');
        };
    }
}
