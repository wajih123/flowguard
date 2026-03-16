package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flash_credits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashCreditEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRepayment;

    @Column(nullable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CreditStatus status = CreditStatus.PENDING;

    @Column
    private Instant disbursedAt;

    @Column
    private Instant repaidAt;

    @Column(nullable = false)
    private Instant dueDate;

    @Column(precision = 6, scale = 2)
    private BigDecimal taegPercent;

    @Column
    private Instant retractionDeadline;

    @Column
    @Builder.Default
    private boolean retractionExercised = false;

    /**
     * Client-supplied idempotency key (UUID format) — prevents duplicate credit requests
     * on network retry. Unique per user; key is honoured for 24 hours after first use.
     * See: FlashCreditResource.requestCredit() — header: Idempotency-Key
     */
    @Column(name = "idempotency_key", length = 36, unique = true)
    private String idempotencyKey;

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

    public enum CreditStatus {
        PENDING, APPROVED, DISBURSED, REPAID, OVERDUE, REJECTED, RETRACTED
    }
}
