package com.flowguard.repository;

import com.flowguard.domain.TaxEstimateEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TaxEstimateRepository implements PanacheRepositoryBase<TaxEstimateEntity, UUID> {

    public List<TaxEstimateEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY dueDate ASC", userId);
    }

    public List<TaxEstimateEntity> findUpcoming(UUID userId, LocalDate from, LocalDate to) {
        return list("user.id = ?1 AND dueDate BETWEEN ?2 AND ?3 AND paidAt IS NULL ORDER BY dueDate ASC",
                userId, from, to);
    }

    public List<TaxEstimateEntity> findUnpaidByUserId(UUID userId) {
        return list("user.id = ?1 AND paidAt IS NULL ORDER BY dueDate ASC", userId);
    }
}
