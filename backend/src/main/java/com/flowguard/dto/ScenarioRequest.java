package com.flowguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ScenarioRequest(
    @NotBlank(message = "Type de scénario requis")
    String type,

    @NotNull(message = "Montant requis")
    @Positive(message = "Montant doit être positif")
    BigDecimal amount,

    @NotNull(message = "Nombre de jours requis")
    @Positive(message = "Nombre de jours doit être positif")
    Integer delayDays,

    String description
) {}
