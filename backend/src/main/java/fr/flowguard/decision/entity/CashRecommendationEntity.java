package fr.flowguard.decision.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "cash_recommendations")
public class CashRecommendationEntity extends PanacheEntityBase {

    @Id public String id;

    @Column(name = "snapshot_id", nullable = false) public String snapshotId;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "action_type", nullable = false) public String actionType;
    @Column(name = "description", nullable = false) public String description;
    @Column(name = "estimated_impact") public BigDecimal estimatedImpact;
    @Column(name = "horizon_days") public Integer horizonDays;
    @Column(name = "confidence") public BigDecimal confidence;
    @Column(name = "status", nullable = false) public String status = "PENDING";
    @Column(name = "applied_at") public Instant appliedAt;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static List<CashRecommendationEntity> findBySnapshot(String snapshotId) {
        return list("snapshotId = ?1 ORDER BY estimatedImpact DESC", snapshotId);
    }

    public static List<CashRecommendationEntity> findPendingByUser(String userId) {
        return list("userId = ?1 AND status = 'PENDING' ORDER BY createdAt DESC", userId);
    }

    public static Optional<CashRecommendationEntity> findByIdAndUser(String id, String userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResultOptional();
    }
}