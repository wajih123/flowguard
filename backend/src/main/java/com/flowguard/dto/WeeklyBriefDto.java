package com.flowguard.dto;

public record WeeklyBriefDto(
        String id,
        String briefText,
        String riskLevel,
        int runwayDays,
        String generatedAt,
        String generationMode
) {}
