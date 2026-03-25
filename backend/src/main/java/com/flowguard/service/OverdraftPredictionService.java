package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OverdraftPredictionService {

    @Inject
    IncomeProfileService incomeProfileService;

    public static class OverdraftRisk {
        public String accountId;
        public String riskLevel; // NONE, LOW, MODERATE, HIGH, CRITICAL
        public Double daysUntilOverdraft;
        public BigDecimal currentBalance;
        public BigDecimal averageDailySpend;
        public BigDecimal projectedBalanceIn7Days;
        public BigDecimal projectedBalanceIn30Days;
        public List<String> riskFactors;
    }

    /**
     * Predicts overdraft risk for an account
     */
    public OverdraftRisk predictOverdraftRisk(String accountId) {
        OverdraftRisk risk = new OverdraftRisk();
        risk.accountId = accountId;
        risk.riskFactors = new ArrayList<>();

        AccountEntity account = AccountEntity.findById(accountId);
        if (account == null) {
            risk.riskLevel = "UNKNOWN";
            return risk;
        }

        risk.currentBalance = account.getBalance();

        // Calculate average daily spend
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        Instant cutoffTime = thirtyDaysAgo.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        PanacheQuery<TransactionEntity> query = TransactionEntity.find(
            "accountId = ?1 AND createdAt >= ?2 AND type = 'EXPENSE' ORDER BY createdAt DESC",
            accountId, cutoffTime
        );

        List<TransactionEntity> transactions = query.list();
        BigDecimal totalSpent = transactions.stream()
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int days = 30;
        risk.averageDailySpend = totalSpent.divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP);

        // Project balance
        BigDecimal dailySpend = risk.averageDailySpend;
        risk.projectedBalanceIn7Days = risk.currentBalance.subtract(dailySpend.multiply(BigDecimal.valueOf(7)));
        risk.projectedBalanceIn30Days = risk.currentBalance.subtract(dailySpend.multiply(BigDecimal.valueOf(30)));

        // Determine risk level and factors
        if (risk.currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            risk.riskLevel = "CRITICAL";
            risk.riskFactors.add("Account already has negative balance");
            risk.daysUntilOverdraft = 0.0;
        } else if (risk.projectedBalanceIn7Days.compareTo(BigDecimal.ZERO) < 0) {
            risk.riskLevel = "HIGH";
            risk.daysUntilOverdraft = calculateDaysUntilOverdraft(risk.currentBalance, dailySpend);
            risk.riskFactors.add("Overdraft likely within 7 days");
        } else if (risk.projectedBalanceIn30Days.compareTo(BigDecimal.ZERO) < 0) {
            risk.riskLevel = "MODERATE";
            risk.daysUntilOverdraft = calculateDaysUntilOverdraft(risk.currentBalance, dailySpend);
            risk.riskFactors.add("Overdraft likely within 30 days");
        } else if (risk.currentBalance.compareTo(BigDecimal.valueOf(100)) < 0) {
            risk.riskLevel = "LOW";
            risk.daysUntilOverdraft = calculateDaysUntilOverdraft(risk.currentBalance, dailySpend);
            risk.riskFactors.add("Low balance cushion");
        } else {
            risk.riskLevel = "NONE";
            risk.daysUntilOverdraft = calculateDaysUntilOverdraft(risk.currentBalance, dailySpend);
        }

        return risk;
    }

    private Double calculateDaysUntilOverdraft(BigDecimal balance, BigDecimal dailySpend) {
        if (dailySpend.compareTo(BigDecimal.ZERO) == 0) {
            return Double.MAX_VALUE;
        }
        return balance.divide(dailySpend, 1, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
