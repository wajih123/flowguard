package com.flowguard.repository;

import com.flowguard.domain.BudgetCategoryEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BudgetCategoryRepository implements PanacheRepositoryBase<BudgetCategoryEntity, UUID> {

    public List<BudgetCategoryEntity> findByUserAndPeriod(UUID userId, int year, int month) {
        return list("user.id = ?1 AND periodYear = ?2 AND periodMonth = ?3", userId, year, month);
    }

    public Optional<BudgetCategoryEntity> findByUserPeriodCategory(UUID userId, int year, int month, String category) {
        return find("user.id = ?1 AND periodYear = ?2 AND periodMonth = ?3 AND category = ?4",
                userId, year, month, category).firstResultOptional();
    }

    public List<BudgetCategoryEntity> findByUserIdAndYear(UUID userId, int year) {
        return list("user.id = ?1 AND periodYear = ?2 ORDER BY periodMonth ASC, category ASC", userId, year);
    }
}
