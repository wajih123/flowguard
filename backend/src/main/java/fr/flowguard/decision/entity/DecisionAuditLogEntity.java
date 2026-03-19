package fr.flowguard.decision.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "decision_audit_log")
public class DecisionAuditLogEntity extends PanacheEntityBase {

    @Id public String id;

    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "event_type", nullable = false) public String eventType;
    @Column(name = "entity_id") public String entityId;
    @Column(name = "entity_type") public String entityType;
    @Column(name = "payload", columnDefinition = "jsonb") public String payload;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static DecisionAuditLogEntity of(String userId, String eventType, String entityId, String entityType, String payload) {
        DecisionAuditLogEntity e = new DecisionAuditLogEntity();
        e.userId = userId;
        e.eventType = eventType;
        e.entityId = entityId;
        e.entityType = entityType;
        e.payload = payload;
        return e;
    }
}