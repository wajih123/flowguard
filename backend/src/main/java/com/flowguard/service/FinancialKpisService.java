package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.TransactionEntity;
import com.flowguard.dto.FinancialKpisDto;
import com.flowguard.repository.AccountRepository;
import com.flowguard.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FinancialKpisService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionRepository transactionRepository;

    public FinancialKpisDto computeKpis(UUID userId) {
        List<AccountEntity> accounts = accountRepository.findByUserId(userId);
        if (accounts.isEmpty()) {
            throw new IllegalStateException("Aucun compte bancaire connecté.");
        }

        BigDecimal currentBalance = accounts.stream()
                .map(AccountEntity::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate now = LocalDate.now();
        LocalDate thirtyDaysAgo = now.minusDays(30);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal monthlyBurnRate = BigDecimal.ZERO;
        BigDecimal dailyBurnRate = BigDecimal.ZERO;

        int dso = 0;
        int dpo = 0;
        BigDecimal bfr = BigDecimal.ZERO;
        int runwayDays = 0;

        for (AccountEntity account : accounts) {
            List<TransactionEntity> txs = transactionRepository.findByAccountIdAndDateBetween(account.getId(), thirtyDaysAgo, now);
            for (TransactionEntity tx : txs) {
                if (tx.getType() == TransactionEntity.TransactionType.CREDIT) {
                    totalIncome = totalIncome.add(tx.getAmount());
                } else {
                    totalExpenses = totalExpenses.add(tx.getAmount());
                }
            }
        }

        monthlyBurnRate = totalExpenses;
        dailyBurnRate = monthlyBurnRate.divide(BigDecimal.valueOf(30), 2, BigDecimal.ROUND_HALF_UP);
        runwayDays = dailyBurnRate.compareTo(BigDecimal.ZERO) > 0 ? currentBalance.divide(dailyBurnRate, 0, BigDecimal.ROUND_DOWN).intValue() : 0;

        // DSO/DPO/BFR: placeholders, real calculation would require invoices

        return new FinancialKpisDto(
                bfr,
                dso,
                dpo,
                monthlyBurnRate,
                dailyBurnRate,
                runwayDays,
                totalIncome,
                totalExpenses,
                currentBalance
        );
    }
}
