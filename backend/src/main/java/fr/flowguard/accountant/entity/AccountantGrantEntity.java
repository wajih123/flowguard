package fr.flowguard.accountant.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "accountant_grants", indexes = {
    @Index(name = "idx_accountant_grants_user", columnList = "user_id"),
    @Index(name = "idx_accountant_grants_token", columnList = "access_token", unique = true)
})
public class AccountantGrantEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "accountant_email", nullable = false)
    public String accountantEmail;

    @Column(name = "access_token", unique = true, nullable = false)
    public String accessToken;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static List<AccountantGrantEntity> findByUser(String userId) {
        return find("userId = ?1 ORDER BY createdAt DESC", userId).list();
    }

    public static AccountantGrantEntity findByToken(String token) {
        return find("accessToken = ?1", token).firstResult();
    }

    public static AccountantGrantEntity findByIdAndUser(String id, String userId) {
        return find("id = ?1 AND userId = ?2", id, userId).firstResult();
    }
}