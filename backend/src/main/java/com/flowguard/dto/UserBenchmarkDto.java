package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * User performance vs sector benchmarks for a given metric.
 */
public record UserBenchmarkDto(
        String sector,
        String companySize,
        String metricName,
        BigDecimal userValue,
        String unit,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        /** BOTTOM_25, Q1_Q2, Q2_Q3, TOP_25 */
        String percentileBand,
        /** Human-readable label, e.g. "Votre trésorerie dépasse 72% du secteur." */
        String insight
) {}
