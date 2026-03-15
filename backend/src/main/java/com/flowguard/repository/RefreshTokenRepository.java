package com.flowguard.repository;

import com.flowguard.domain.RefreshTokenEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepositoryBase<RefreshTokenEntity, UUID> {

    public Optional<RefreshTokenEntity> findByHash(String tokenHash) {
        return find("tokenHash = ?1", tokenHash).firstResultOptional();
    }

    /** Revoke all active tokens for a user (logout all devices). */
    public long revokeAllForUser(UUID userId) {
        return update(
            "revoked = true, revokedAt = ?1 WHERE user.id = ?2 AND revoked = false",
            Instant.now(), userId
        );
    }

    /** Purge expired tokens older than 90 days (housekeeping). */
    public long purgeExpired() {
        return delete("expiresAt < ?1", Instant.now().minusSeconds(90L * 86400));
    }
}
