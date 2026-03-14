package com.flowguard.domain;

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

    @Column(nullable = false, unique = true)
    private String iban;

    @Column(nullable = false)
    private String bic;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column
    private String bankName;

    @Column
    private String accountName;

    @Column
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.OK;

    @Column
    private Instant lastSyncAt;

    @Column
    private String externalAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column
    private LocalDate lastSyncDate;

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

    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }

    public enum SyncStatus {
        PENDING, SYNCING, OK, ERROR
    }
}
