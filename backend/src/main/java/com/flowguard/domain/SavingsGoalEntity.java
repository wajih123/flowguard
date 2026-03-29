package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoalEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 50)
    @Builder.Default
    private GoalType goalType = GoalType.OTHER;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** User-overridden monthly savings amount. NULL → system recommendation is used. */
    @Column(name = "monthly_contribution", precision = 12, scale = 2)
    private BigDecimal monthlyContribution;

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

    public enum GoalType {
        EMERGENCY_FUND,
        VACATION,
        EQUIPMENT,
        REAL_ESTATE,
        EDUCATION,
        RETIREMENT,
        PROJECT,
        OTHER;

        public String label() {
            return switch (this) {
                case EMERGENCY_FUND -> "Fonds d'urgence";
                case VACATION      -> "Vacances";
                case EQUIPMENT     -> "Équipement";
                case REAL_ESTATE   -> "Immobilier";
                case EDUCATION     -> "Formation";
                case RETIREMENT    -> "Retraite";
                case PROJECT       -> "Projet";
                case OTHER         -> "Objectif personnel";
            };
        }

        public String emoji() {
            return switch (this) {
                case EMERGENCY_FUND -> "🛡️";
                case VACATION      -> "✈️";
                case EQUIPMENT     -> "💻";
                case REAL_ESTATE   -> "🏠";
                case EDUCATION     -> "🎓";
                case RETIREMENT    -> "🌅";
                case PROJECT       -> "🚀";
                case OTHER         -> "🎯";
            };
        }
    }
}
