package com.flowguard.dto;

import com.flowguard.domain.UserEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Full user details for admin detail view (email NOT masked).
 */
public record AdminUserDetailDto(
    UUID   id,
    String firstName,
    String lastName,
    String email,
    String companyName,
    String userType,
    UserEntity.KycStatus kycStatus,
    String role,
    String swanOnboardingId,
    String swanAccountId,
    String nordigenRequisitionId,
    Instant gdprConsentAt,
    Instant dataDeletionRequestedAt,
    boolean disabled,
    Instant disabledAt,
    String disabledReason,
    Instant createdAt,
    Instant updatedAt
) {
    public static AdminUserDetailDto from(UserEntity u) {
        return new AdminUserDetailDto(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            u.getEmail(),
            u.getCompanyName(),
            u.getUserType() != null ? u.getUserType().name() : null,
            u.getKycStatus(),
            u.getRole() != null ? u.getRole() : "ROLE_USER",
            u.getSwanOnboardingId(),
            u.getSwanAccountId(),
            u.getNordigenRequisitionId(),
            u.getGdprConsentAt(),
            u.getDataDeletionRequestedAt(),
            u.isDisabled(),
            u.getDisabledAt(),
            u.getDisabledReason(),
            u.getCreatedAt(),
            u.getUpdatedAt()
        );
    }
}
