package fr.flowguard.budget.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "budget_categories")
public class BudgetCategoryEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "period_year", nullable = false)
    public short periodYear;

    @Column(name = "period_month", nullable = false)
    public short periodMonth;

    @Column(name = "category", nullable = false)
    public String category;

    @Column(name = "budgeted_amount", nullable = false)
    public BigDecimal budgetedAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static List<BudgetCategoryEntity> findByUserAndPeriod(String userId, int year, int month) {
        return list("userId = ?1 AND periodYear = ?2 AND periodMonth = ?3", userId, (short) year, (short) month);
    }

    public static Optional<BudgetCategoryEntity> findByUserPeriodCategory(String userId, int year, int month, String category) {
        return find("userId = ?1 AND periodYear = ?2 AND periodMonth = ?3 AND category = ?4",
                userId, (short) year, (short) month, category).firstResultOptional();
    }
}