package fr.flowguard.tax.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "tax_estimates")
public class TaxEstimateEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "tax_type", nullable = false)
    public String taxType;

    @Column(name = "period_label", nullable = false)
    public String periodLabel;

    @Column(name = "estimated_amount", nullable = false)
    public BigDecimal estimatedAmount;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    @Column(name = "paid_at")
    public Instant paidAt;

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

    public static List<TaxEstimateEntity> findByUser(String userId) {
        return list("userId = ?1 ORDER BY dueDate ASC", userId);
    }

    public static List<TaxEstimateEntity> findUpcomingByUser(String userId) {
        return list("userId = ?1 AND paidAt IS NULL AND dueDate >= ?2 ORDER BY dueDate ASC",
                userId, LocalDate.now());
    }

    public static Optional<TaxEstimateEntity> findByIdAndUser(String id, String userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }
}