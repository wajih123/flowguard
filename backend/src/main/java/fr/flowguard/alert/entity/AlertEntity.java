package fr.flowguard.alert.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "alerts")
public class AlertEntity extends PanacheEntityBase {

    @Id public String id;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "account_id") public String accountId;
    @Column(name = "severity", nullable = false) public String severity;
    @Column(name = "type", nullable = false) public String type;
    @Column(name = "title", nullable = false) public String title;
    @Column(name = "message", nullable = false) public String message;
    @Column(name = "suggested_action") public String suggestedAction;
    @Column(name = "snapshot_id") public String snapshotId;
    @Column(name = "predicted_deficit_amount") public BigDecimal predictedDeficitAmount;
    @Column(name = "predicted_deficit_date") public LocalDate predictedDeficitDate;
    @Column(name = "is_read", nullable = false) public boolean isRead = false;
    @Column(name = "is_dismissed", nullable = false) public boolean isDismissed = false;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static List<AlertEntity> findByUser(String userId, boolean unreadOnly) {
        if (unreadOnly) {
            return list("userId = ?1 AND isRead = false AND isDismissed = false ORDER BY createdAt DESC", userId);
        }
        return list("userId = ?1 AND isDismissed = false ORDER BY createdAt DESC", userId);
    }

    public static long countUnread(String userId) {
        return count("userId = ?1 AND isRead = false AND isDismissed = false", userId);
    }
}