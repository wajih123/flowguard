package com.flowguard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SpendingAnalysisDto(
    UUID accountId,
    String period,
    BigDecimal totalSpent,
    Map<String, BigDecimal> byCategory,
    List<String> insights
) {}
