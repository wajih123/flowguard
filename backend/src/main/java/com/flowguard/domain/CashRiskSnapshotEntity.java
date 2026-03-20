package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cash_risk_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRiskSnapshotEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "computed_at", nullable = false)
    @Builder.Default
    private Instant computedAt = Instant.now();

    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    @Column(name = "runway_days")
    private Integer runwayDays;

    @Column(name = "min_balance", precision = 18, scale = 2)
    private BigDecimal minBalance;

    @Column(name = "min_balance_date")
    private LocalDate minBalanceDate;

    @Column(name = "current_balance", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "volatility_score", precision = 5, scale = 4)
    private BigDecimal volatilityScore;

    @Column(name = "deficit_predicted", nullable = false)
    @Builder.Default
    private boolean deficitPredicted = false;

    @Column(name = "score_version", nullable = false, length = 10)
    @Builder.Default
    private String scoreVersion = "v1";
}
