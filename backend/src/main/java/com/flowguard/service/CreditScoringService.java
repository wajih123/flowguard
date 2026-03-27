package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.FlashCreditEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.FlashCreditRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Scoring interne pour les crédits flash.
 * <p>
 * Critères pondérés (total max = 100) :
 * <ul>
 *   <li>Historique de revenus (30 pts)</li>
 *   <li>Ratio d'endettement (25 pts)</li>
 *   <li>Historique de remboursement (25 pts)</li>
 *   <li>Ancienneté du compte (10 pts)</li>
 *   <li>Stabilité des flux (10 pts)</li>
 * </ul>
 * Seuil d'acceptation : 40/100 minimum.
 */
@ApplicationScoped
public class CreditScoringService {

    public static final int MIN_SCORE = 40;

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    FlashCreditRepository flashCreditRepository;

    /**
     * Calculate a credit score (0-100) for a given user.
     *
     * @return the score
     */
    public int computeScore(UUID userId) {
        int score = 0;
        score += scoreIncome(userId);
        score += scoreDebtRatio(userId);
        score += scoreRepaymentHistory(userId);
        score += scoreAccountAge(userId);
        score += scoreFlowStability(userId);
        // Cap score if the current balance is negative — a direct signal of financial stress regardless of income history.
        int balanceCap = computeBalanceCap(userId);
        // Critical: also cap score based on J+30 projection. A user headed for severe deficit in 30 days
        // should not score high regardless of current balance or income.
        int projectionCap = computeProjectionCap(userId);
        return Math.min(Math.min(Math.min(score, 100), balanceCap), projectionCap);
    }

    /**
     * @throws IllegalStateException if score is below the minimum threshold.
     */
    public void assertEligible(UUID userId) {
        int score = computeScore(userId);
        if (score < MIN_SCORE) {
            throw new IllegalStateException(
                    "Score de crédit insuffisant (" + score + "/100). "
                    + "Minimum requis : " + MIN_SCORE + ". "
                    + "Améliorez votre profil en maintenant des revenus réguliers et en remboursant vos crédits à temps."
            );
        }
    }

    // ---- Component scores ----

    /**
     * Income score (max 30 pts).
     * Based on average monthly income over the last 90 days.
     *   >= 5000€ → 30
     *   >= 3000€ → 24
     *   >= 1500€ → 18
     *   >= 500€  → 10
     *   < 500€   → 0
     */
    int scoreIncome(UUID userId) {
        BigDecimal monthlyIncome = computeMonthlyIncome(userId, 90);
        if (monthlyIncome.compareTo(new BigDecimal("5000")) >= 0) return 30;
        if (monthlyIncome.compareTo(new BigDecimal("3000")) >= 0) return 24;
        if (monthlyIncome.compareTo(new BigDecimal("1500")) >= 0) return 18;
        if (monthlyIncome.compareTo(new BigDecimal("500")) >= 0) return 10;
        return 0;
    }

    /**
     * Debt ratio score (max 25 pts).
     * Ratio = active credit total / monthly income.
     *   < 10% → 25
     *   < 20% → 20
     *   < 33% → 12
     *   >= 33% → 0
     */
    int scoreDebtRatio(UUID userId) {
        BigDecimal monthlyIncome = computeMonthlyIncome(userId, 90);
        if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) return 0;

        // Compute monthly expenses excluding internal transfers
        BigDecimal monthlyExpenses = computeMonthlyExpenses(userId, 90); // ← NEW
        // Total debt ratio = (active credit repayments + monthly expenses) / income
        BigDecimal activeDebt = BigDecimal.ZERO;
        for (FlashCreditEntity c : flashCreditRepository.findActiveByUserId(userId)) {
            activeDebt = activeDebt.add(c.getTotalRepayment());
        }
        BigDecimal totalDebt = activeDebt.add(monthlyExpenses); // ← NEW
        BigDecimal ratio = totalDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP); // ← CHANGED
        if (ratio.compareTo(new BigDecimal("0.10")) < 0) return 25;
        if (ratio.compareTo(new BigDecimal("0.20")) < 0) return 20;
        if (ratio.compareTo(new BigDecimal("0.33")) < 0) return 12;
        return 0;
    }

    /**
     * Repayment history score (max 25 pts).
     * Based on ratio of repaid-on-time credits to total past credits.
     *   100% on-time → 25
     *   >= 80%       → 20
     *   >= 60%       → 12
     *   < 60%        → 0
     * First-time borrowers get a neutral score of 15 pts.
     */
    int scoreRepaymentHistory(UUID userId) {
        List<FlashCreditEntity> allCredits = flashCreditRepository.findByUserId(userId);

        // Exclude currently active credits
        List<FlashCreditEntity> pastCredits = allCredits.stream()
                .filter(c -> c.getStatus() == FlashCreditEntity.CreditStatus.REPAID
                          || c.getStatus() == FlashCreditEntity.CreditStatus.OVERDUE
                          || c.getStatus() == FlashCreditEntity.CreditStatus.RETRACTED)
                .toList();

        if (pastCredits.isEmpty()) {
            return 15; // First-time borrower — neutral score
        }

        long repaidOnTime = pastCredits.stream()
                .filter(c -> c.getStatus() == FlashCreditEntity.CreditStatus.REPAID
                          || c.getStatus() == FlashCreditEntity.CreditStatus.RETRACTED)
                .count();

        double onTimeRatio = (double) repaidOnTime / pastCredits.size();
        if (onTimeRatio >= 1.0) return 25;
        if (onTimeRatio >= 0.80) return 20;
        if (onTimeRatio >= 0.60) return 12;
        return 0;
    }

    /**
     * Account age score (max 10 pts).
     * Based on the age of the oldest connected account.
     *   >= 365 days → 10
     *   >= 180 days → 7
     *   >= 90 days  → 4
     *   < 90 days   → 1
     */
    int scoreAccountAge(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) return 0;

        Instant oldest = accounts.stream()
                .map(AccountEntity::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());

        long daysOld = ChronoUnit.DAYS.between(oldest, Instant.now());
        if (daysOld >= 365) return 10;
        if (daysOld >= 180) return 7;
        if (daysOld >= 90) return 4;
        return 1;
    }

    /**
     * Cash flow stability score (max 10 pts).
     * Measures the coefficient of variation (stddev / mean) of weekly income over the last 12 weeks.
     *   CV < 0.2  → 10  (very stable)
     *   CV < 0.5  → 7
     *   CV < 1.0  → 4
     *   CV >= 1.0 → 0   (highly irregular)
     * If insufficient data, returns a neutral 5 pts.
     */
    int scoreFlowStability(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) return 0;

        LocalDate now = LocalDate.now();
        LocalDate twelveWeeksAgo = now.minusWeeks(12);

        // Compute weekly income totals
        double[] weeklyIncome = new double[12];
        for (AccountEntity account : accounts) {
            List<TransactionEntity> credits = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), twelveWeeksAgo, now)
                    .stream()
                    .filter(t -> t.getType() == TransactionEntity.TransactionType.CREDIT)
                    .toList();

            for (TransactionEntity t : credits) {
                long daysSinceStart = ChronoUnit.DAYS.between(twelveWeeksAgo, t.getDate());
                int weekIndex = (int) (daysSinceStart / 7);
                if (weekIndex >= 0 && weekIndex < 12) {
                    weeklyIncome[weekIndex] += t.getAmount().doubleValue();
                }
            }
        }

        // Check if there's enough data (at least 4 weeks with income)
        long weeksWithIncome = 0;
        for (double w : weeklyIncome) {
            if (w > 0) weeksWithIncome++;
        }
        if (weeksWithIncome < 4) return 5;

        // Coefficient of variation
        double mean = 0;
        for (double w : weeklyIncome) mean += w;
        mean /= 12.0;
        if (mean <= 0) return 0;

        double variance = 0;
        for (double w : weeklyIncome) variance += (w - mean) * (w - mean);
        variance /= 12.0;
        double cv = Math.sqrt(variance) / mean;

        if (cv < 0.2) return 10;
        if (cv < 0.5) return 7;
        if (cv < 1.0) return 4;
        return 0;
    }

    // ---- Helper ----

    /**
     * Compute monthly expenses excluding internal transfers.
     */
    private BigDecimal computeMonthlyExpenses(UUID userId, int lookbackDays) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) return BigDecimal.ZERO;

        LocalDate from = LocalDate.now().minusDays(lookbackDays);
        BigDecimal totalExpenses = BigDecimal.ZERO;
        for (AccountEntity account : accounts) {
            List<TransactionEntity> debits = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), from, LocalDate.now())
                    .stream()
                    .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT)
                    .filter(t -> !isInternalTransfer(t.getLabel()))  // ← Filter internal transfers
                    .toList();
            for (TransactionEntity t : debits) {
                totalExpenses = totalExpenses.add(t.getAmount());
            }
        }
        // Return monthly average
        long days = Math.max(1, ChronoUnit.DAYS.between(from, LocalDate.now()));
        return totalExpenses.divide(new BigDecimal(days), 2, RoundingMode.HALF_UP).multiply(new BigDecimal("30"));
    }

    /**
     * Returns the maximum score allowed based on the total current balance.
     * A negative balance caps the score regardless of other components:
     *   >= 0       → 100 (no cap)
     *   < 0        →  45 (Moyen)
     *   < -200     →  40
     *   < -1000    →  30 (Fragile)
     *   < -5000    →  20 (Critique)
     */
    private int computeBalanceCap(UUID userId) {
        // Use only ACTIVE accounts — closed/disconnected accounts should not
        // penalise the score with their historical negative balance.
        BigDecimal totalBalance = accountRepository.findActiveByUserId(userId).stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalBalance.compareTo(BigDecimal.ZERO) >= 0) return 100;
        double bal = totalBalance.doubleValue();
        if (bal <= -5000) return 20;
        if (bal <= -1000) return 30;
        if (bal <= -200)  return 40;
        return 45;
    }

    /**
     * Cap score based on J+30 cash flow projection.
     * If the user is headed for a large deficit in 30 days, they should not score high.
     *   J+30 balance >= 0   → 100 (no cap)
     *   J+30 balance < 0    →  45
     *   J+30 balance < -500 →  35 (significant deficit)
     *   J+30 balance < -2000→  25 (critical trajectory)
     */
    private int computeProjectionCap(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findActiveByUserId(userId);
        if (accounts.isEmpty()) return 100;

        // Compute projected balance = current balance + avg daily net flow × 30
        BigDecimal currentBalance = accounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgDailyNet = computeAverageDailyNetFlow(userId, 60); // Last 60 days
        BigDecimal projectedBalance = currentBalance.add(
            avgDailyNet.multiply(new BigDecimal("30"))
        );

        double proj = projectedBalance.doubleValue();
        if (proj >= 0) return 100;
        if (proj <= -2000) return 25;
        if (proj <= -500) return 35;
        return 45;
    }

    /**
     * Compute average daily net flow (income - expenses) over the last N days.
     * Filters out internal transfers (same user between own accounts).
     */
    private BigDecimal computeAverageDailyNetFlow(UUID userId, int lookbackDays) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) return BigDecimal.ZERO;

        LocalDate from = LocalDate.now().minusDays(lookbackDays);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (AccountEntity account : accounts) {
            List<TransactionEntity> txns = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), from, LocalDate.now());
            for (TransactionEntity t : txns) {
                // Skip internal transfers (label contains "Vir Inst vers" or "Virement Web")
                if (isInternalTransfer(t.getLabel())) continue;

                if (t.getType() == TransactionEntity.TransactionType.CREDIT) {
                    income = income.add(t.getAmount());
                } else {
                    expenses = expenses.add(t.getAmount());
                }
            }
        }

        long days = Math.max(1, ChronoUnit.DAYS.between(from, LocalDate.now()));
        return income.subtract(expenses).divide(new BigDecimal(days), 2, RoundingMode.HALF_UP);
    }

    /** Detect if a transaction is an internal transfer (between user's own accounts). */
    private boolean isInternalTransfer(String label) {
        if (label == null) return false;
        return label.toLowerCase().contains("vir inst vers")
            || label.toLowerCase().contains("virement web")
            || label.toLowerCase().contains("virement de")
            || label.toLowerCase().contains("vir de")
            || label.toLowerCase().contains("ret dab");
    }

    private BigDecimal computeMonthlyIncome(UUID userId, int lookbackDays) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) return BigDecimal.ZERO;

        LocalDate from = LocalDate.now().minusDays(lookbackDays);
        BigDecimal totalIncome = BigDecimal.ZERO;
        for (AccountEntity account : accounts) {
            List<TransactionEntity> credits = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), from, LocalDate.now())
                    .stream()
                    .filter(t -> t.getType() == TransactionEntity.TransactionType.CREDIT)
                    .filter(t -> !isInternalTransfer(t.getLabel()))  // ← NEW: Filter internal transfers
                    .toList();
            for (TransactionEntity t : credits) {
                totalIncome = totalIncome.add(t.getAmount());
            }
        }

        int months = Math.max(lookbackDays / 30, 1);
        return totalIncome.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
