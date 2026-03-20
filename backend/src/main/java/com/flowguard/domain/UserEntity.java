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

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "company_name", nullable = true)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "swan_onboarding_id")
    private String swanOnboardingId;

    @Column(name = "swan_account_id")
    private String swanAccountId;

    @Column(name = "nordigen_requisition_id")
    private String nordigenRequisitionId;

    @Column(name = "bridge_user_uuid")
    private String bridgeUserUuid;

    @Column(name = "gdpr_consent_at")
    private Instant gdprConsentAt;

    @Column(name = "data_deletion_requested_at")
    private Instant dataDeletionRequestedAt;

    @Column(nullable = false)
    @Builder.Default
    private String role = "ROLE_USER";

    @Column(nullable = false)
    @Builder.Default
    private boolean disabled = false;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum UserType {
        INDIVIDUAL, FREELANCE, TPE, PME, SME,
        B2C_SALARIE, B2C_FAMILLE, B2C_RETRAITE, B2C_ETUDIANT, B2C_CADRE
    }

    public enum KycStatus {
        PENDING, IN_PROGRESS, APPROVED, REJECTED
    }
}
