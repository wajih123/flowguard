package com.flowguard.dto;

import java.math.BigDecimal;

public record SimulateResultDto(
        String scenarioType,
        BigDecimal baseBalance,
        BigDecimal projectedBalance,
        BigDecimal balanceDelta,
        int baseRunwayDays,
        int projectedRunwayDays,
        String explanation
) {}
