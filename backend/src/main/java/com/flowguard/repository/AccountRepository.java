package com.flowguard.repository;

import com.flowguard.domain.AccountEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountRepository implements PanacheRepositoryBase<AccountEntity, UUID> {

    public List<AccountEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY createdAt DESC", userId);
    }

    public Optional<AccountEntity> findByIban(String iban) {
        return find("iban", iban).firstResultOptional();
    }

    public Optional<AccountEntity> findByExternalAccountId(String externalAccountId) {
        return find("externalAccountId", externalAccountId).firstResultOptional();
    }

    public long countByUserId(UUID userId) {
        return count("user.id", userId);
    }

    /** Returns the earliest account creation date for a user, or null if they have no accounts. */
    public Instant findOldestCreatedAt(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt ASC", userId)
                .firstResultOptional()
                .map(AccountEntity::getCreatedAt)
                .orElse(null);
    }
}
