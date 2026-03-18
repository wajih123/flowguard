package com.flowguard.dto;

import com.flowguard.domain.SectorBenchmarkEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record SectorBenchmarkDto(
        UUID id,
        String sector,
        String companySize,
        String metricName,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        String unit
) {
    public static SectorBenchmarkDto from(SectorBenchmarkEntity e) {
        return new SectorBenchmarkDto(e.getId(), e.getSector(), e.getCompanySize(),
                e.getMetricName(), e.getP25(), e.getP50(), e.getP75(), e.getUnit());
    }
}
