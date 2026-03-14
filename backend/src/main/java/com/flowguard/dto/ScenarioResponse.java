package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioResponse(
    List<BigDecimal> baselineForecast,
    List<BigDecimal> impactedForecast,
    BigDecimal maxImpact,
    BigDecimal minBalance,
    BigDecimal worstDeficit,
    int daysUntilImpact,
    String riskLevel,
    String recommendation
) {}
