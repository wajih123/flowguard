package com.flowguard.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRequest(
        @NotBlank String clientName,
        String clientEmail,
        @NotBlank String number,
        @NotNull @DecimalMin("0.01") BigDecimal amountHt,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
        @NotBlank String currency,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        String notes
) {}
