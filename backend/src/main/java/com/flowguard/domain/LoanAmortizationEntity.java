package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_amortizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAmortizationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 100)
    private String loanName; // "Personal Loan", "Home Loan", etc.

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingBalance;

    @Column(nullable = false)
    private Double interestRate; // in percentage

    @Column(nullable = false)
    private Integer totalMonths;

    @Column(nullable = false)
    private Integer monthsElapsed;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    @Column
    private LocalDate nextPaymentDate;

    @Column(nullable = false, length = 20)
    private String status; // active, completed, defaulted

    @Column(columnDefinition = "TEXT")
    private String bankDetails;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
