package com.flowguard.service;

import com.flowguard.domain.RefreshTokenEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.RefreshTokenRepository;
import com.flowguard.repository.UserRepository;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages refresh token lifecycle with rotation (RFC 6749 §10.4).
 *
 * <p>Flow:
 * <ol>
 *   <li>Issue: generate opaque token, store its SHA-256 hash in DB with 30d TTL.
 *   <li>Rotate: on each /auth/refresh call, revoke old token, issue new access + refresh pair.
 *   <li>Revoke: on logout, revoke all tokens for the user.
 * </ol>
 *
 * <p>Access token lifespan: 15 minutes (fintech standard — not 1 hour).
 */
@ApplicationScoped
public class RefreshTokenService {

    private static final Duration ACCESS_TOKEN_TTL  = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    RefreshTokenRepository tokenRepo;

    @Inject
    UserRepository userRepo;

    /**
     * Generates and persists a new refresh token for the given user.
     *
     * @return the raw (plaintext) opaque refresh token to return to the client
     */
    @Transactional
    public String issueRefreshToken(UserEntity user, String ipAddress, String deviceInfo) {
        // Generate 32 bytes of random data, then Base64url-encode
        byte[] raw = new byte[32];
        SECURE_RANDOM.nextBytes(raw);
        String opaqueToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        String tokenHash = sha256(opaqueToken);

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHash)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
                .build();

        tokenRepo.persist(entity);
        return opaqueToken;
    }

    /**
     * Rotates a refresh token: validates the old one, revokes it, issues new pair.
     *
     * @param rawRefreshToken the opaque refresh token from the client
     * @return new {accessToken, refreshToken}
     * @throws SecurityException if the token is invalid, expired, or revoked
     */
    @Transactional
    public TokenPair rotate(String rawRefreshToken, String ipAddress) {
        String hash = sha256(rawRefreshToken);

        RefreshTokenEntity token = tokenRepo.findByHash(hash)
                .orElseThrow(() -> new SecurityException("Refresh token invalide"));

        if (!token.isValid()) {
            // Potential token reuse attack — revoke ALL tokens for this user
            tokenRepo.revokeAllForUser(token.getUser().getId());
            throw new SecurityException(
                "Refresh token expiré ou révoqué. Toutes les sessions ont été terminées pour sécurité.");
        }

        // Revoke consumed token (rotation)
        token.setRevoked(true);
        token.setRevokedAt(Instant.now());

        UserEntity user = token.getUser();

        // Issue new access token (15 min)
        String newAccessToken = buildAccessToken(user);

        // Issue new refresh token (30 days)
        String newRefreshToken = issueRefreshToken(user, ipAddress, token.getDeviceInfo());

        return new TokenPair(newAccessToken, newRefreshToken, user);
    }

    /**
     * Revokes a single token by its SHA-256 hash (single-device logout).
     */
    @Transactional
    public void revokeByHash(String tokenHash) {
        tokenRepo.findByHash(tokenHash).ifPresent(t -> {
            t.setRevoked(true);
            t.setRevokedAt(Instant.now());
        });
    }

    /**
     * Revokes all active refresh tokens for a user (global logout).
     */
    @Transactional
    public void revokeAll(UUID userId) {
        tokenRepo.revokeAllForUser(userId);
    }

    /**
     * Builds a signed JWT access token with 15-minute lifespan.
     */
    public String buildAccessToken(UserEntity user) {
        return Jwt.issuer("https://flowguard.fr")
                .subject(user.getId().toString())
                .groups(resolveGroups(user))
                .claim("email", user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("userType", user.getUserType().name())
                .expiresIn(ACCESS_TOKEN_TTL)
                .sign();
    }

    private java.util.Set<String> resolveGroups(UserEntity user) {
        return switch (user.getRole()) {
            case "ROLE_SUPER_ADMIN" -> java.util.Set.of("super_admin", "admin", "user");
            case "ROLE_ADMIN"       -> java.util.Set.of("admin", "user");
            default                 -> java.util.Set.of("user");
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /** Immutable pair returned from {@link #rotate}. */
    public record TokenPair(String accessToken, String refreshToken, UserEntity user) {}
}
