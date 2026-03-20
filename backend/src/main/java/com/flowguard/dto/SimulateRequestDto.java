package com.flowguard.dto;

public record SimulateRequestDto(
        String scenarioType,
        Double amount,
        Double percentage,
        Integer daysDelay
) {}
