package com.flowguard.service;

import com.flowguard.domain.AccountEntity;
import com.flowguard.domain.SweepSuggestionEntity;
import com.flowguard.domain.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class SweepSuggestionService {

    /**
     * Generates sweep suggestions for a user's accounts
     */
    public List<SweepSuggestionEntity> generateSweepSuggestions(String userId) {
        List<SweepSuggestionEntity> suggestions = new ArrayList<>();

        PanacheQuery<AccountEntity> accounts = AccountEntity.find(
            "userId = ?1 AND status = 'active' ORDER BY balance DESC",
            userId
        );

        List<AccountEntity> userAccounts = accounts.list();
        if (userAccounts.size() < 2) {
            return suggestions;
        }

        // Sort by interest rate (highest first for target accounts)
        List<AccountEntity> targetAccounts = new ArrayList<>(userAccounts);
        targetAccounts.sort((a, b) -> {
            Double rateA = getAccountInterestRate(a);
            Double rateB = getAccountInterestRate(b);
            return rateB.compareTo(rateA);
        });

        // Check each excess balance account
        for (AccountEntity source : userAccounts) {
            BigDecimal excessBalance = calculateExcessBalance(source);

            if (excessBalance.compareTo(BigDecimal.ZERO) > 0) {
                // Find best target account
                for (AccountEntity target : targetAccounts) {
                    if (!source.getId().equals(target.getId()) && 
                        getAccountInterestRate(target) > getAccountInterestRate(source)) {

                        SweepSuggestionEntity suggestion = new SweepSuggestionEntity();
                        suggestion.setUser((UserEntity) UserEntity.find("id = ?1", userId).firstResult());
                        suggestion.setFromAccount(source);
                        suggestion.setToAccount(target);
                        suggestion.setSuggestedAmount(excessBalance);
                        suggestion.setReason("excess_cash");
                        suggestion.setStatus("suggested");

                        Double savings = calculateEstimatedSavings(excessBalance, source, target);
                        suggestion.setEstimatedEarnings(savings);

                        String analysis = String.format(
                            "Move %.2f from %s (%.2f%% interest) to %s (%.2f%% interest) for %.2f in annual savings",
                            excessBalance.doubleValue(), source.getAccountName(), 
                            getAccountInterestRate(source),
                            target.getAccountName(), getAccountInterestRate(target),
                            savings
                        );
                        suggestion.setAnalysis(analysis);

                        suggestions.add(suggestion);
                        break;
                    }
                }
            }
        }

        return suggestions;
    }

    /**
     * Calculates excess balance (amount above minimum threshold)
     */
    private BigDecimal calculateExcessBalance(AccountEntity account) {
        BigDecimal balance = account.getBalance();
        BigDecimal minimumBalance = BigDecimal.valueOf(500); // Minimum safety threshold

        if (balance.compareTo(minimumBalance) > 0) {
            return balance.subtract(minimumBalance);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Gets interest rate for an account (mock implementation)
     */
    private Double getAccountInterestRate(AccountEntity account) {
        // This would normally be fetched from account type master data
        String accountType = account.getAccountType();

        if ("SAVINGS".equals(accountType)) {
            return 3.5;
        } else if ("MONEY_MARKET".equals(accountType)) {
            return 4.2;
        } else if ("CHECKING".equals(accountType)) {
            return 0.01;
        }

        return 0.5;
    }

    /**
     * Calculates estimated annual savings from moving funds
     */
    private Double calculateEstimatedSavings(BigDecimal amount, AccountEntity source, AccountEntity target) {
        Double sourceRate = getAccountInterestRate(source);
        Double targetRate = getAccountInterestRate(target);
        Double rateDifference = targetRate - sourceRate;

        return amount.doubleValue() * rateDifference / 100.0;
    }

    /**
     * Executes a sweep suggestion
     */
    public void executeSweep(String suggestionId) {
        SweepSuggestionEntity suggestion = SweepSuggestionEntity.findById(suggestionId);
        if (suggestion == null) {
            throw new IllegalArgumentException("Sweep suggestion not found");
        }

        if (!"suggested".equals(suggestion.getStatus())) {
            throw new IllegalArgumentException("Can only execute suggested sweeps");
        }

        // Update suggestion status
        suggestion.setStatus("executed");
        suggestion.setUpdatedAt(Instant.now());

        // Here you would normally call a transfer service to execute the actual transfer
        // For now, we just update the status
        suggestion.persist();
    }
}
