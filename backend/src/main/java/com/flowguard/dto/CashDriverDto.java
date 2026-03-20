package com.flowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashDriverDto(
        String id,
        String type,
        String label,
        BigDecimal amount,
        int impactDays,
        LocalDate dueDate,
        String referenceId,
        String referenceType,
        int rank
) {}
