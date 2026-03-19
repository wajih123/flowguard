package fr.flowguard.decision.service;

import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.TransactionEntity;
import fr.flowguard.decision.entity.CashDriverEntity;
import fr.flowguard.decision.entity.CashRiskSnapshotEntity;
import fr.flowguard.tax.entity.TaxEstimateEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computes a deterministic cash risk score for a user.
 *
 * Risk Levels:
 *   CRITICAL – runway < 7d OR deficit predicted this week
 *   HIGH     – runway < 21d OR total obligations > 80% of balance
 *   MEDIUM   – runway < 45d OR total obligations > 40% of balance
 *   LOW      – otherwise
 *
 * Inputs:
 *   - Current bank balance (real-time)
 *   - ML forecast min_balance + deficit_predicted (passed in)
 *   - Upcoming tax/payroll obligations (from DB)
 *   - Cash balance volatility (stddev of last 30d daily balances)
 */
@ApplicationScoped
public class RiskScoringService {

    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("5000");
    private static final int SCORE_VERSION = 1;

    /**
     * Build and persist a CashRiskSnapshotEntity.
     *
     * @param userId           user
     * @param minForecastBalance  from ML forecast (null if ML unavailable)
     * @param minForecastDate     from ML forecast (null if ML unavailable)
     * @param deficitPredicted    from ML forecast
     * @param drivers          already-detected drivers for this snapshot
     */
    @Transactional
    public CashRiskSnapshotEntity computeAndPersist(
            String userId,
            BigDecimal minForecastBalance,
            LocalDate minForecastDate,
            boolean deficitPredicted,
            List<CashDriverEntity> drivers) {

        // Current balances
        List<BankAccountEntity> accounts = BankAccountEntity.findActiveByUserId(userId);
        BigDecimal currentBalance = accounts.stream()
                .map(a -> a.currentBalance != null ? a.currentBalance : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Upcoming obligations in next 30 days
        BigDecimal totalObligations = BigDecimal.ZERO;
        for (TaxEstimateEntity tax : TaxEstimateEntity.findUpcomingByUser(userId)) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), tax.dueDate);
            if (days >= 0 && days <= 30) {
                totalObligations = totalObligations.add(tax.estimatedAmount);
            }
        }
        // Add obligatory driver amounts (SUPPLIER_PAYMENT, RECURRING_COST)
        for (CashDriverEntity d : drivers) {
            if ("SUPPLIER_PAYMENT".equals(d.driverType) || "RECURRING_COST".equals(d.driverType)) {
                if (d.amount != null) totalObligations = totalObligations.add(d.amount);
            }
        }

        // Runway days: how many days until balance drops below threshold
        int runwayDays = computeRunway(userId, currentBalance, totalObligations);

        // Volatility: coefficient of variation of last 30d transaction amounts
        BigDecimal volatility = computeVolatility(accounts.isEmpty() ? null : accounts.get(0).id);

        // Projected min balance (use ML if available)
        BigDecimal effectiveMinBalance = minForecastBalance != null
                ? minForecastBalance
                : currentBalance.subtract(totalObligations);
        LocalDate effectiveMinDate = minForecastDate != null ? minForecastDate : LocalDate.now().plusDays(runwayDays);

        // Risk classification
        String riskLevel = classify(runwayDays, totalObligations, currentBalance, deficitPredicted, effectiveMinBalance);

        CashRiskSnapshotEntity snap = new CashRiskSnapshotEntity();
        snap.userId = userId;
        snap.riskLevel = riskLevel;
        snap.runwayDays = runwayDays;
        snap.minBalance = effectiveMinBalance;
        snap.minBalanceDate = effectiveMinDate;
        snap.currentBalance = currentBalance;
        snap.volatilityScore = volatility;
        snap.deficitPredicted = deficitPredicted;
        snap.scoreVersion = "v" + SCORE_VERSION;
        snap.persist();
        return snap;
    }

    private String classify(int runwayDays, BigDecimal obligations, BigDecimal balance,
                            boolean deficitPredicted, BigDecimal minBalance) {
        if (deficitPredicted || runwayDays < 7 || minBalance.compareTo(BigDecimal.ZERO) < 0) {
            return "CRITICAL";
        }
        double obRatio = balance.compareTo(BigDecimal.ZERO) > 0
                ? obligations.doubleValue() / balance.doubleValue() : 1.0;
        if (runwayDays < 21 || obRatio > 0.80) return "HIGH";
        if (runwayDays < 45 || obRatio > 0.40) return "MEDIUM";
        return "LOW";
    }

    /**
     * Estimates runway as: balance / avg_daily_spend, capped at 180.
     */
    private int computeRunway(String userId, BigDecimal currentBalance, BigDecimal nearTermObligations) {
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) return 0;
        // Simple heuristic: daily burn = obligations / 30
        BigDecimal dailyBurn = nearTermObligations.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        if (dailyBurn.compareTo(BigDecimal.ZERO) <= 0) return 180;
        BigDecimal available = currentBalance.subtract(LOW_BALANCE_THRESHOLD);
        if (available.compareTo(BigDecimal.ZERO) <= 0) return 0;
        int days = available.divide(dailyBurn, 0, RoundingMode.FLOOR).intValue();
        return Math.min(days, 180);
    }

    /**
     * Coefficient of variation (stddev / mean) of transaction magnitudes in last 30 days.
     * Returns 0 if insufficient data.
     */
    private BigDecimal computeVolatility(String accountId) {
        if (accountId == null) return BigDecimal.ZERO;
        List<TransactionEntity> txs = TransactionEntity
                .find("accountId = ?1 AND transactionDate >= ?2",
                        accountId, LocalDate.now().minusDays(30))
                .list();
        if (txs.size() < 3) return BigDecimal.ZERO;
        double[] amounts = txs.stream()
                .filter(t -> t.amount != null)
                .mapToDouble(t -> Math.abs(t.amount.doubleValue()))
                .toArray();
        if (amounts.length < 2) return BigDecimal.ZERO;
        double mean = 0;
        for (double a : amounts) mean += a;
        mean /= amounts.length;
        if (mean == 0) return BigDecimal.ZERO;
        double variance = 0;
        for (double a : amounts) variance += Math.pow(a - mean, 2);
        variance /= amounts.length;
        double cv = Math.sqrt(variance) / mean;
        return BigDecimal.valueOf(cv).min(new BigDecimal("9.9999")).setScale(4, RoundingMode.HALF_UP);
    }
}