package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRecommendationEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "snapshot_id", nullable = false, length = 36)
    private String snapshotId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_impact", precision = 18, scale = 2)
    private BigDecimal estimatedImpact;

    @Column(name = "horizon_days")
    private Integer horizonDays;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
