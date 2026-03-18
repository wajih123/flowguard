package com.flowguard.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentInitiationRequest(
        @NotBlank String creditorName,
        @NotBlank @Size(min = 15, max = 34) String creditorIban,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 255) String reference
) {}
