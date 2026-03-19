package fr.flowguard.benchmark.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "sector_benchmarks")
public class SectorBenchmarkEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "sector", nullable = false)
    public String sector;

    @Column(name = "company_size", nullable = false)
    public String companySize;

    @Column(name = "metric_name", nullable = false)
    public String metricName;

    @Column(name = "p25", nullable = false)
    public BigDecimal p25;

    @Column(name = "p50", nullable = false)
    public BigDecimal p50;

    @Column(name = "p75", nullable = false)
    public BigDecimal p75;

    @Column(name = "unit", nullable = false)
    public String unit = "EUR";

    public static List<String> findDistinctSectors() {
        return find("SELECT DISTINCT b.sector FROM SectorBenchmarkEntity b ORDER BY b.sector")
                .project(String.class).list();
    }

    public static List<SectorBenchmarkEntity> findBySectorAndSize(String sector, String companySize) {
        return list("sector = ?1 AND companySize = ?2", sector, companySize);
    }
}