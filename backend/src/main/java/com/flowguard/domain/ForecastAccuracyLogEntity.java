package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "forecast_accuracy_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastAccuracyLogEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Column(nullable = false)
    private int horizonDays;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal predictedBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal actualBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal mae;

    @Column(nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();
}
