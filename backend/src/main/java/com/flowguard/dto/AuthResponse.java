package com.flowguard.dto;

import com.flowguard.domain.UserEntity;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserResponse user
) {
    public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String companyName,
        UserEntity.UserType userType,
        UserEntity.KycStatus kycStatus,
        String role,
        boolean emailVerified
    ) {}
}
