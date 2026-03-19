package fr.flowguard.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "users")
public class UserEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "email", nullable = false, unique = true)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "first_name", nullable = false)
    public String firstName;

    @Column(name = "last_name", nullable = false)
    public String lastName;

    @Column(name = "role", nullable = false)
    public String role = "ROLE_USER";

    @Column(name = "kyc_status", nullable = false)
    public String kycStatus = "PENDING";

    @Column(name = "reserve_enabled", nullable = false)
    public boolean reserveEnabled = false;

    @Column(name = "email_verified", nullable = false)
    public boolean emailVerified = false;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static Optional<UserEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public static boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }
}