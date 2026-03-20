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

    @Column(name = "total_repayment", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRepayment;

    @Column(nullable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CreditStatus status = CreditStatus.PENDING;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "repaid_at")
    private Instant repaidAt;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Column(name = "taeg_percent", precision = 6, scale = 2)
    private BigDecimal taegPercent;

    @Column(name = "retraction_deadline")
    private Instant retractionDeadline;

    @Column(name = "retraction_exercised")
    @Builder.Default
    private boolean retractionExercised = false;

    /**
     * Client-supplied idempotency key (UUID format) — prevents duplicate credit requests
     * on network retry. Unique per user; key is honoured for 24 hours after first use.
     * See: FlashCreditResource.requestCredit() — header: Idempotency-Key
     */
    @Column(name = "idempotency_key", length = 36, unique = true)
    private String idempotencyKey;

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

    public enum CreditStatus {
        PENDING, APPROVED, DISBURSED, REPAID, OVERDUE, REJECTED, RETRACTED
    }
}
