package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cash_drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashDriverEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "snapshot_id", nullable = false, length = 36)
    private String snapshotId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "driver_type", nullable = false, length = 40)
    private String driverType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String label;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "impact_days")
    private Integer impactDays;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reference_id", length = 36)
    private String referenceId;

    @Column(name = "reference_type", length = 20)
    private String referenceType;

    @Column(nullable = false)
    @Builder.Default
    private short rank = 1;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
