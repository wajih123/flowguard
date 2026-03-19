package fr.flowguard.decision.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "cash_drivers")
public class CashDriverEntity extends PanacheEntityBase {

    @Id public String id;

    @Column(name = "snapshot_id", nullable = false) public String snapshotId;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "driver_type", nullable = false) public String driverType;
    @Column(name = "label", nullable = false) public String label;
    @Column(name = "amount") public BigDecimal amount;
    @Column(name = "impact_days") public Integer impactDays;
    @Column(name = "due_date") public LocalDate dueDate;
    @Column(name = "reference_id") public String referenceId;
    @Column(name = "reference_type") public String referenceType;
    @Column(name = "rank") public short rank;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static List<CashDriverEntity> findBySnapshot(String snapshotId) {
        return list("snapshotId = ?1 ORDER BY rank ASC", snapshotId);
    }

    public static List<CashDriverEntity> findLatestByUser(String userId, int limit) {
        return find("userId = ?1 ORDER BY createdAt DESC", userId).page(0, limit).list();
    }
}