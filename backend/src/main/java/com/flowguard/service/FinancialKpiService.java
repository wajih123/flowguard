package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.FinancialKpisDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calcul des KPIs financiers (BFR, DSO, DPO, cash burn rate, runway).
 */
@ApplicationScoped
public class FinancialKpiService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    public FinancialKpisDto computeKpis(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Aucun compte bancaire connecté.");
        }

        LocalDate now = LocalDate.now();
        LocalDate thirtyDaysAgo = now.minusDays(30);
        LocalDate ninetyDaysAgo = now.minusDays(90);

        BigDecimal currentBalance = BigDecimal.ZERO;
        for (AccountEntity a : accounts) {
            currentBalance = currentBalance.add(a.getBalance());
        }

        // Aggregate 30-day income/expenses
        BigDecimal income30d = BigDecimal.ZERO;
        BigDecimal expenses30d = BigDecimal.ZERO;
        // For DSO/DPO: 90-day client payments and supplier payments
        BigDecimal clientPayments90d = BigDecimal.ZERO;
        BigDecimal supplierPayments90d = BigDecimal.ZERO;
        BigDecimal totalRevenue90d = BigDecimal.ZERO;
        BigDecimal totalPurchases90d = BigDecimal.ZERO;

        for (AccountEntity account : accounts) {
            // 30-day transactions
            List<TransactionEntity> txns30d = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), thirtyDaysAgo, now);

            for (TransactionEntity t : txns30d) {
                if (t.getType() == TransactionEntity.TransactionType.CREDIT) {
                    income30d = income30d.add(t.getAmount());
                } else {
                    expenses30d = expenses30d.add(t.getAmount());
                }
            }

            // 90-day transactions (for DSO/DPO)
            List<TransactionEntity> txns90d = transactionRepository
                    .findByAccountIdAndDateBetween(account.getId(), ninetyDaysAgo, now);

            for (TransactionEntity t : txns90d) {
                if (t.getType() == TransactionEntity.TransactionType.CREDIT) {
                    totalRevenue90d = totalRevenue90d.add(t.getAmount());
                    if (t.getCategory() == TransactionEntity.TransactionCategory.CLIENT_PAYMENT) {
                        clientPayments90d = clientPayments90d.add(t.getAmount());
                    }
                } else {
                    totalPurchases90d = totalPurchases90d.add(t.getAmount());
                    if (t.getCategory() == TransactionEntity.TransactionCategory.FOURNISSEUR) {
                        supplierPayments90d = supplierPayments90d.add(t.getAmount());
                    }
                }
            }
        }

        // DSO = (créances clients / CA total) × 90 jours
        int dso = 0;
        if (totalRevenue90d.compareTo(BigDecimal.ZERO) > 0) {
            // Outstanding receivables ≈ revenue - payments received from clients
            BigDecimal outstandingReceivables = totalRevenue90d.subtract(clientPayments90d).max(BigDecimal.ZERO);
            dso = outstandingReceivables
                    .multiply(BigDecimal.valueOf(90))
                    .divide(totalRevenue90d, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        // DPO = (dettes fournisseurs / achats totaux) × 90 jours
        int dpo = 0;
        if (totalPurchases90d.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal outstandingPayables = totalPurchases90d.subtract(supplierPayments90d).max(BigDecimal.ZERO);
            dpo = outstandingPayables
                    .multiply(BigDecimal.valueOf(90))
                    .divide(totalPurchases90d, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        // BFR = créances clients + stocks - dettes fournisseurs
        // (stocks not tracked; BFR ≈ DSO part - DPO part of daily revenue)
        BigDecimal dailyRevenue = totalRevenue90d.divide(BigDecimal.valueOf(90), 2, RoundingMode.HALF_UP);
        BigDecimal bfr = dailyRevenue.multiply(BigDecimal.valueOf(dso - dpo)).setScale(2, RoundingMode.HALF_UP);

        // Cash burn rate (net monthly consumption)
        BigDecimal monthlyBurn = expenses30d.subtract(income30d);
        BigDecimal dailyBurn = monthlyBurn.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        // Runway (days of cash remaining at current burn rate)
        int runwayDays;
        if (dailyBurn.compareTo(BigDecimal.ZERO) > 0) {
            runwayDays = currentBalance.divide(dailyBurn, 0, RoundingMode.FLOOR).intValue();
            runwayDays = Math.max(runwayDays, 0);
        } else {
            runwayDays = 999; // Not burning cash → infinite runway (capped)
        }

        return new FinancialKpisDto(
                bfr,
                dso,
                dpo,
                monthlyBurn,
                dailyBurn,
                runwayDays,
                income30d,
                expenses30d,
                currentBalance
        );
    }
}
