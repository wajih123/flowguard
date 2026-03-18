package com.flowguard.dto;

import com.flowguard.domain.AccountantAccessEntity;

import java.time.Instant;
import java.util.UUID;

public record AccountantAccessDto(
        UUID id,
        String accountantEmail,
        /** Returned only on creation — hidden afterwards for security */
        String accessToken,
        Instant expiresAt,
        Instant createdAt,
        boolean expired
) {
    /** Full response on creation — includes token. */
    public static AccountantAccessDto fromFull(AccountantAccessEntity e) {
        return new AccountantAccessDto(e.getId(), e.getAccountantEmail(),
                e.getAccessToken(), e.getExpiresAt(), e.getCreatedAt(), e.isExpired());
    }

    /** Redacted response for listing — token masked. */
    public static AccountantAccessDto fromRedacted(AccountantAccessEntity e) {
        return new AccountantAccessDto(e.getId(), e.getAccountantEmail(),
                "***", e.getExpiresAt(), e.getCreatedAt(), e.isExpired());
    }
}
