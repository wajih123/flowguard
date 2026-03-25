package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bnpl_installments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BnplInstallmentEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private String merchantName;

    @Column(nullable = false, length = 50)
    private String provider; // Klarna, Scalapay, etc.

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private Integer totalInstallments;

    @Column(nullable = false)
    private Integer paidInstallments;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal nextPaymentAmount;

    @Column(nullable = false)
    private LocalDate nextPaymentDate;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private String status; // active, completed, defaulted

    @Column(columnDefinition = "TEXT")
    private String transactionReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
