package com.flowguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Email requis")
    @Email(message = "Email invalide")
    String email,

    @NotBlank(message = "Mot de passe requis")
    @Size(min = 8, message = "Minimum 8 caractères")
    String password
) {}
