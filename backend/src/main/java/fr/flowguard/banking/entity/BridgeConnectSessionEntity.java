package fr.flowguard.banking.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bridge_connect_sessions")
public class BridgeConnectSessionEntity extends PanacheEntityBase {

    @Id @Column(name = "id") public String id;
    @Column(name = "user_id", nullable = false) public String userId;
    @Column(name = "connect_url", nullable = false) public String connectUrl;
    @Column(name = "state", nullable = false, unique = true) public String state;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "used", nullable = false) public boolean used = false;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static BridgeConnectSessionEntity findByState(String state) {
        return find("state", state).firstResult();
    }

    public boolean isValid() {
        return !used && Instant.now().isBefore(expiresAt);
    }
}