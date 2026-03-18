package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tax_estimates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxEstimateEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * TVA, URSSAF, IS (impôt sociétés), IR (impôt revenu), CFE
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxType taxType;

    /** e.g. "2026-T1", "2026-07" */
    @Column(nullable = false)
    private String periodLabel;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column
    private Instant paidAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum TaxType {
        TVA, URSSAF, IS, IR, CFE
    }
}
