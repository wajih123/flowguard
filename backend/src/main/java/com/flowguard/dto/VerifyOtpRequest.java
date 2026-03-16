package com.flowguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /auth/verify-otp.
 */
public record VerifyOtpRequest(
        @NotBlank
        String sessionToken,

        @NotBlank
        @Size(min = 6, max = 6, message = "Le code doit comporter exactement 6 chiffres")
        @Pattern(regexp = "\\d{6}", message = "Le code doit contenir uniquement des chiffres")
        String code) {}
