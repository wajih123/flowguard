package com.flowguard.dto;

import com.flowguard.domain.UserEntity;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String companyName,
    UserEntity.UserType userType,
    UserEntity.KycStatus kycStatus,
    Instant createdAt
) {
    public static UserDto from(UserEntity entity) {
        return new UserDto(
            entity.getId(),
            entity.getFirstName(),
            entity.getLastName(),
            entity.getEmail(),
            entity.getCompanyName(),
            entity.getUserType(),
            entity.getKycStatus(),
            entity.getCreatedAt()
        );
    }
}
