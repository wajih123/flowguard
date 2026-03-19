package fr.flowguard.banking.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bridge_users")
public class BridgeUserEntity extends PanacheEntityBase {

    @Id @Column(name = "id") public String id;
    @Column(name = "user_id", nullable = false, unique = true) public String userId;
    @Column(name = "bridge_uuid", nullable = false, unique = true) public String bridgeUuid;
    @Column(name = "bridge_email", nullable = false) public String bridgeEmail;
    @Column(name = "bridge_password") public String bridgePassword;
    @Column(name = "bridge_access_token") public String bridgeAccessToken;
    @Column(name = "bridge_token_expires_at") public Instant bridgeTokenExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;

    @PrePersist void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static BridgeUserEntity findByUserId(String userId) {
        return find("userId", userId).firstResult();
    }

    public boolean isTokenValid() {
        return bridgeAccessToken != null && bridgeTokenExpiresAt != null
            && Instant.now().isBefore(bridgeTokenExpiresAt.minusSeconds(60));
    }
}