package com.flowguard.repository;

import com.flowguard.domain.FlashCreditEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FlashCreditRepository implements PanacheRepositoryBase<FlashCreditEntity, UUID> {

    public List<FlashCreditEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY createdAt DESC", userId);
    }

    public List<FlashCreditEntity> findActiveByUserId(UUID userId) {
        return list("user.id = ?1 AND status IN ('APPROVED', 'DISBURSED') ORDER BY createdAt DESC", userId);
    }

    public long countActiveByUserId(UUID userId) {
        return count("user.id = ?1 AND status IN ('APPROVED', 'DISBURSED')", userId);
    }

    /**
     * Find all DISBURSED credits that are past their due date (candidates for OVERDUE).
     */
    public List<FlashCreditEntity> findOverdueCandidates(Instant now) {
        return list("status = 'DISBURSED' AND dueDate < ?1", now);
    }

    /**
     * Find a credit by idempotency key (for deduplication on retry).
     * Returns the existing credit if the key was already used.
     */
    public java.util.Optional<FlashCreditEntity> findByIdempotencyKey(String idempotencyKey) {
        return find("idempotencyKey = ?1", idempotencyKey).firstResultOptional();
    }
}
