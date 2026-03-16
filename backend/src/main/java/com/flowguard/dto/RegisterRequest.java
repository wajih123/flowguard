package com.flowguard.dto;

import com.flowguard.domain.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Prénom requis")
    @Size(min = 2, message = "Minimum 2 caractères")
    String firstName,

    @NotBlank(message = "Nom requis")
    @Size(min = 2, message = "Minimum 2 caractères")
    String lastName,

    @NotBlank(message = "Email requis")
    @Email(message = "Email invalide")
    String email,

    @NotBlank(message = "Mot de passe requis")
    @Size(min = 8, message = "Minimum 8 caractères")
    String password,

    String companyName,

    @NotNull(message = "Type d'utilisateur requis")
    UserEntity.UserType userType
) {}
