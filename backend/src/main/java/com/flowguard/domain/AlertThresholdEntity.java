package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * User-customizable alert thresholds.
 * Each user can override the default alert behaviour per alert type.
 */
@Entity
@Table(name = "alert_thresholds", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "alert_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertThresholdEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertEntity.AlertType alertType;

    /** Minimum absolute amount to trigger the alert (e.g. ignore deficits < 500€). */
    @Column(name = "min_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    /** Whether this alert type is enabled for the user. */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Minimum severity to notify (e.g. only HIGH and above). */
    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false)
    @Builder.Default
    private AlertEntity.Severity minSeverity = AlertEntity.Severity.LOW;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
