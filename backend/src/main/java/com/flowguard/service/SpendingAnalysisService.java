package com.flowguard.service;

import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.SpendingAnalysisDto;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpendingAnalysisService {

    @Inject
    TransactionRepository transactionRepository;

    public SpendingAnalysisDto analyze(UUID accountId, LocalDate from, LocalDate to) {
        List<TransactionEntity> transactions = transactionRepository
                .findByAccountIdAndDateBetween(accountId, from, to);

        List<TransactionEntity> debits = transactions.stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT)
                .toList();

        BigDecimal totalSpent = debits.stream()
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = debits.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getAmount, BigDecimal::add)
                ));

        List<String> insights = generateInsights(byCategory, totalSpent);

        String period = from.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + " / " + to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        return new SpendingAnalysisDto(accountId, period, totalSpent, byCategory, insights);
    }

    /**
     * Returns spending per category for a specific year/month.
     * Used by BudgetService for Budget vs Actual comparison.
     */
    public Map<String, BigDecimal> getSpendingByCategory(UUID userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        // Aggregate across all user accounts
        List<TransactionEntity> allTx = transactionRepository
                .findByUserIdAndDateBetween(userId, from, to);
        return allTx.stream()
                .filter(t -> t.getType() == TransactionEntity.TransactionType.DEBIT)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory() != null ? t.getCategory().name() : "AUTRE",
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getAmount, BigDecimal::add)
                ));
    }

    private List<String> generateInsights(Map<String, BigDecimal> byCategory, BigDecimal totalSpent) {
        List<String> insights = new ArrayList<>();

        if (totalSpent.compareTo(BigDecimal.ZERO) == 0) {
            insights.add("Aucune dépense sur cette période.");
            return insights;
        }

        // Top category
        byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    BigDecimal pct = entry.getValue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalSpent, 1, RoundingMode.HALF_UP);
                    insights.add("Catégorie principale : " + entry.getKey()
                            + " (" + pct + "% des dépenses).");
                });

        // Recurring subscriptions warning
        if (byCategory.containsKey("ABONNEMENT")) {
            BigDecimal aboPct = byCategory.get("ABONNEMENT")
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalSpent, 1, RoundingMode.HALF_UP);
            if (aboPct.compareTo(BigDecimal.valueOf(20)) > 0) {
                insights.add("Les abonnements représentent " + aboPct
                        + "% de vos dépenses. Pensez à les revoir.");
            }
        }

        return insights;
    }
}
