package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.domain.TransactionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class IncomeProfileService {

    @Inject
    UserService userService;

    public static class IncomeProfile {
        public Double averageMonthlyIncome;
        public Double minMonthlyIncome;
        public Double maxMonthlyIncome;
        public String regularity; // REGULAR, IRREGULAR, HIGHLY_IRREGULAR
        public List<Double> monthlyIncomes;
        public Double standardDeviation;
        public LocalDate analysisStartDate;
        public LocalDate analysisEndDate;
    }

    /**
     * Analyzes user's income over the past 12 months
     */
    public IncomeProfile analyzeIncomeProfile(String userId, int monthsBack) {
        IncomeProfile profile = new IncomeProfile();
        profile.monthlyIncomes = new ArrayList<>();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(monthsBack);
        profile.analysisStartDate = startDate;
        profile.analysisEndDate = endDate;

        // Fetch all income transactions (positive amounts)
        PanacheQuery<TransactionEntity> query = TransactionEntity.find(
            "userId = ?1 AND createdAt >= ?2 AND createdAt <= ?3 AND type = 'INCOME' ORDER BY createdAt DESC",
            userId, startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC), endDate.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC)
        );

        Map<YearMonth, BigDecimal> monthlyTotals = new HashMap<>();
        for (TransactionEntity tx : query.list()) {
            YearMonth month = YearMonth.from(tx.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
            monthlyTotals.merge(month, tx.getAmount(), BigDecimal::add);
        }

        if (monthlyTotals.isEmpty()) {
            profile.averageMonthlyIncome = 0.0;
            profile.minMonthlyIncome = 0.0;
            profile.maxMonthlyIncome = 0.0;
            profile.regularity = "UNKNOWN";
            profile.standardDeviation = 0.0;
            return profile;
        }

        List<Double> incomes = monthlyTotals.values().stream()
            .map(BigDecimal::doubleValue)
            .collect(Collectors.toList());

        profile.monthlyIncomes = incomes;
        profile.averageMonthlyIncome = incomes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        profile.minMonthlyIncome = incomes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        profile.maxMonthlyIncome = incomes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // Calculate coefficient of variation for regularity assessment
        double variance = incomes.stream()
            .mapToDouble(x -> Math.pow(x - profile.averageMonthlyIncome, 2))
            .average()
            .orElse(0.0);
        profile.standardDeviation = Math.sqrt(variance);

        double coefficientOfVariation = (profile.standardDeviation / profile.averageMonthlyIncome) * 100;
        if (coefficientOfVariation < 15) {
            profile.regularity = "REGULAR";
        } else if (coefficientOfVariation < 40) {
            profile.regularity = "IRREGULAR";
        } else {
            profile.regularity = "HIGHLY_IRREGULAR";
        }

        return profile;
    }

    /**
     * Calculates sustainable income (conservative estimate for lending decisions)
     */
    public double calculateSustainableIncome(String userId) {
        IncomeProfile profile = analyzeIncomeProfile(userId, 12);
        
        if ("REGULAR".equals(profile.regularity)) {
            return profile.averageMonthlyIncome;
        } else if ("IRREGULAR".equals(profile.regularity)) {
            // Use 70% of average for irregular income
            return profile.averageMonthlyIncome * 0.7;
        } else {
            // Use minimum for highly irregular
            return profile.minMonthlyIncome;
        }
    }
}
