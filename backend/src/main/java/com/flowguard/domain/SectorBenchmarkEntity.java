package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sector_benchmarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sector", "company_size", "metric_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorBenchmarkEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. IT_FREELANCE, CONSULTING, ECOMMERCE, FOOD_BEVERAGE */
    @Column(nullable = false)
    private String sector;

    /** SOLO, SMALL, MEDIUM */
    @Column(name = "company_size", nullable = false)
    private String companySize;

    /** e.g. monthly_revenue, runway_days, burn_rate_pct */
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(precision = 14, scale = 2)
    private BigDecimal p25;

    @Column(precision = 14, scale = 2)
    private BigDecimal p50;

    @Column(precision = 14, scale = 2)
    private BigDecimal p75;

    /** EUR, days, percent */
    @Column
    private String unit;

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
