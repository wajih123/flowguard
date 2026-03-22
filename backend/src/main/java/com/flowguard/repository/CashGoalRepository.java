package com.flowguard.repository;

import com.flowguard.domain.CashGoalEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CashGoalRepository implements PanacheRepositoryBase<CashGoalEntity, UUID> {

    public Optional<CashGoalEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId).firstResultOptional();
    }

    public void deleteByUserId(UUID userId) {
        delete("user.id", userId);
    }
}
