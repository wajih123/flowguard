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
        return Math.min(score, 100);
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

        BigDecimal activeDebt = BigDecimal.ZERO;
        for (FlashCreditEntity c : flashCreditRepository.findActiveByUserId(userId)) {
            activeDebt = activeDebt.add(c.getTotalRepayment());
        }

        BigDecimal ratio = activeDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
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
                    .toList();
            for (TransactionEntity t : credits) {
                totalIncome = totalIncome.add(t.getAmount());
            }
        }

        int months = Math.max(lookbackDays / 30, 1);
        return totalIncome.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
