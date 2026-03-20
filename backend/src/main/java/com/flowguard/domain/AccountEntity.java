package com.flowguard.domain;

import com.flowguard.security.EncryptedStringConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private String iban;

    @Column(nullable = false)
    private String bic;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_type")
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.OK;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "external_account_id", unique = true)
    private String externalAccountId;

    /** Bridge OAuth access token — stored AES-256-GCM encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bridge_access_token", length = 1024)
    private String bridgeAccessToken;

    /** Bridge OAuth refresh token — stored AES-256-GCM encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bridge_refresh_token", length = 1024)
    private String bridgeRefreshToken;

    @Column(name = "bridge_consent_expires_at")
    private Instant bridgeConsentExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "last_sync_date")
    private LocalDate lastSyncDate;

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

    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }

    public enum SyncStatus {
        PENDING, SYNCING, OK, ERROR
    }
}
