package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column
    private String swanOnboardingId;

    @Column
    private String swanAccountId;

    @Column
    private String nordigenRequisitionId;

    @Column
    private String bridgeUserUuid;

    @Column
    private Instant gdprConsentAt;

    @Column
    private Instant dataDeletionRequestedAt;

    @Column(nullable = false)
    @Builder.Default
    private String role = "ROLE_USER";

    @Column(nullable = false)
    @Builder.Default
    private boolean disabled = false;

    @Column
    private Instant disabledAt;

    @Column(length = 500)
    private String disabledReason;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum UserType {
        INDIVIDUAL, FREELANCE, TPE, PME, SME
    }

    public enum KycStatus {
        PENDING, IN_PROGRESS, APPROVED, REJECTED
    }
}
