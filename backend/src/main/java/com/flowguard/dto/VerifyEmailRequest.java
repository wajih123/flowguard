package com.flowguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /auth/verify-email.
 */
public record VerifyEmailRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 6, max = 6, message = "Le code doit comporter exactement 6 chiffres")
        @Pattern(regexp = "\\d{6}", message = "Le code doit contenir uniquement des chiffres")
        String code) {}
