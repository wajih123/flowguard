package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_initiations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private String creditorName;

    /** Validated IBAN — basic format check done in service */
    @Column(nullable = false)
    private String creditorIban;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    /** Unstructured remittance info / payment reference */
    @Column
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Swan payment ID returned after submission */
    @Column
    private String swanPaymentId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant initiatedAt = Instant.now();

    @Column
    private Instant executedAt;

    @Column(length = 36, unique = true)
    private String idempotencyKey;

    public enum PaymentStatus {
        PENDING, SUBMITTED, EXECUTED, REJECTED, CANCELLED
    }
}
