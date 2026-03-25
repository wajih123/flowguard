package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sweep_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SweepSuggestionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    private AccountEntity fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", nullable = false)
    private AccountEntity toAccount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal suggestedAmount;

    @Column(nullable = false, length = 50)
    private String reason; // excess_cash, idle_balance, interest_optimization

    @Column(nullable = false)
    private Double estimatedEarnings; // potential savings/earnings

    @Column(nullable = false, length = 20)
    private String status; // suggested, approved, executed, rejected

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
