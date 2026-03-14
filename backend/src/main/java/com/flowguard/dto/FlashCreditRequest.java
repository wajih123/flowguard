package com.flowguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FlashCreditRequest(
    @NotNull(message = "Montant requis")
    @DecimalMin(value = "500", message = "Montant minimum 500 €")
    BigDecimal amount,

    @NotBlank(message = "Motif requis")
    String purpose
) {}
