package fr.flowguard.decision.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "cash_risk_snapshots")
public class CashRiskSnapshotEntity extends PanacheEntityBase {

    @Id public String id;

    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "computed_at", nullable = false) public Instant computedAt;
    @Column(name = "risk_level", nullable = false) public String riskLevel;
    @Column(name = "runway_days") public Integer runwayDays;
    @Column(name = "min_balance") public BigDecimal minBalance;
    @Column(name = "min_balance_date") public LocalDate minBalanceDate;
    @Column(name = "current_balance") public BigDecimal currentBalance;
    @Column(name = "volatility_score") public BigDecimal volatilityScore;
    @Column(name = "deficit_predicted", nullable = false) public boolean deficitPredicted;
    @Column(name = "score_version", nullable = false) public String scoreVersion = "v1";

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (computedAt == null) computedAt = Instant.now();
    }

    public static Optional<CashRiskSnapshotEntity> findLatestByUser(String userId) {
        return find("userId = ?1 ORDER BY computedAt DESC", userId).firstResultOptional();
    }

    public static List<CashRiskSnapshotEntity> findByUser(String userId, int limit) {
        return find("userId = ?1 ORDER BY computedAt DESC", userId).page(0, limit).list();
    }
}