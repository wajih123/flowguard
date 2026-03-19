package fr.flowguard.alert.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "alert_thresholds")
public class AlertThresholdEntity extends PanacheEntityBase {

    @Id public String id;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "alert_type", nullable = false) public String alertType;
    @Column(name = "min_amount") public BigDecimal minAmount;
    @Column(name = "enabled", nullable = false) public boolean enabled = true;
    @Column(name = "min_severity", nullable = false) public String minSeverity = "LOW";

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

    public static List<AlertThresholdEntity> findByUser(String userId) {
        return list("userId", userId);
    }

    public static Optional<AlertThresholdEntity> findByUserAndType(String userId, String alertType) {
        return find("userId = ?1 AND alertType = ?2", userId, alertType).firstResultOptional();
    }
}