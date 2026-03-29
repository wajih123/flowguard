package com.flowguard.repository;

import com.flowguard.domain.SavingsGoalEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SavingsGoalRepository implements PanacheRepositoryBase<SavingsGoalEntity, UUID> {

    public List<SavingsGoalEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY createdAt ASC", userId);
    }

    public Optional<SavingsGoalEntity> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = ?1 AND user.id = ?2", id, userId).firstResultOptional();
    }

    public void deleteByIdAndUserId(UUID id, UUID userId) {
        delete("id = ?1 AND user.id = ?2", id, userId);
    }
}
