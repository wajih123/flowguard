package com.flowguard.dto;

import com.flowguard.domain.UserEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight user row for admin lists (email partially masked).
 */
public record AdminUserDto(
    UUID   id,
    String firstName,
    String lastName,
    String emailMasked,
    UserEntity.UserType userType,
    UserEntity.KycStatus kycStatus,
    String role,
    boolean disabled,
    Instant createdAt
) {
    /** e.g. "jo***@ex***.com" */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        String maskedLocal  = local.length() <= 2 ? local : local.substring(0, 2) + "***";
        int dotIdx = domain.lastIndexOf('.');
        String domainBase = dotIdx > 0 ? domain.substring(0, dotIdx) : domain;
        String tld         = dotIdx > 0 ? domain.substring(dotIdx) : "";
        String maskedDomain = domainBase.length() <= 2 ? domainBase : domainBase.substring(0, 2) + "***";
        return maskedLocal + "@" + maskedDomain + tld;
    }

    public static AdminUserDto from(com.flowguard.domain.UserEntity u) {
        String role = u.getRole() != null ? u.getRole() : "ROLE_USER";
        return new AdminUserDto(
            u.getId(),
            u.getFirstName(),
            u.getLastName(),
            maskEmail(u.getEmail()),
            u.getUserType(),
            u.getKycStatus(),
            role,
            u.isDisabled(),
            u.getCreatedAt()
        );
    }
}
