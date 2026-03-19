package fr.flowguard.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "email_verifications")
public class EmailVerificationEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "otp_code", nullable = false)
    public String otpCode;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "used", nullable = false)
    public boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static Optional<EmailVerificationEntity> findActiveByEmail(String email) {
        return find("email = ?1 and used = false and expiresAt > ?2", email, Instant.now())
                .firstResultOptional();
    }
}