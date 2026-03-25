package com.flowguard.repository;

import com.flowguard.domain.TransactionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<TransactionEntity, UUID> {

    public List<TransactionEntity> findByAccountId(UUID accountId) {
        return list("account.id = ?1 ORDER BY date DESC", accountId);
    }

    public List<TransactionEntity> findByAccountIdAndDateBetween(UUID accountId, LocalDate from, LocalDate to) {
        return list("account.id = ?1 AND date >= ?2 AND date <= ?3 ORDER BY date DESC", accountId, from, to);
    }

    public List<TransactionEntity> findByAccountIdAndCategory(UUID accountId, TransactionEntity.TransactionCategory category) {
        return list("account.id = ?1 AND category = ?2 ORDER BY date DESC", accountId, category);
    }

    public List<TransactionEntity> findRecurringByAccountId(UUID accountId) {
        return list("account.id = ?1 AND isRecurring = true ORDER BY date DESC", accountId);
    }

    /** Aggregate across all accounts belonging to a user — for budget vs actual. */
    public List<TransactionEntity> findByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to) {
        return list("account.user.id = ?1 AND date >= ?2 AND date <= ?3 ORDER BY date DESC",
                userId, from, to);
    }

    /** All recurring transactions across all accounts for a user. */
    public List<TransactionEntity> findRecurringByUserId(UUID userId) {
        return list("account.user.id = ?1 AND isRecurring = true ORDER BY date DESC", userId);
    }

    /** Deduplication check used during CSV import. */
    public boolean existsByAccountIdDateLabelAmount(UUID accountId, LocalDate date, String label, BigDecimal amount) {
        return count("account.id = ?1 AND date = ?2 AND label = ?3 AND amount = ?4",
                accountId, date, label, amount) > 0;
    }
}
