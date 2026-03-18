package com.flowguard.service;

import com.flowguard.domain.AccountantAccessEntity;
import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AccountantAccessDto;
import com.flowguard.repository.AccountantAccessRepository;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AccountantAccessService {

    private static final int TOKEN_VALIDITY_DAYS = 90;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject AccountantAccessRepository accessRepo;
    @Inject UserRepository userRepository;
    @Inject PushNotificationService notificationService;

    public List<AccountantAccessDto> listGrants(UUID ownerId) {
        return accessRepo.findByOwnerId(ownerId)
                .stream().map(AccountantAccessDto::fromRedacted).toList();
    }

    /**
     * Grant read-only access to an accountant. If a grant already exists for this email,
     * the existing token is revoked and a new one is issued.
     */
    @Transactional
    public AccountantAccessDto grantAccess(UUID ownerId, String accountantEmail) {
        UserEntity owner = userRepository.findByIdOptional(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Revoke existing grant for this email if any
        accessRepo.findByOwnerAndEmail(ownerId, accountantEmail)
                .ifPresent(existing -> accessRepo.delete(existing));

        String token = generateSecureToken();
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);

        AccountantAccessEntity grant = AccountantAccessEntity.builder()
                .owner(owner)
                .accountantEmail(accountantEmail)
                .accessToken(token)
                .expiresAt(expiresAt)
                .build();
        accessRepo.persist(grant);

        // Notify accountant by email
        notificationService.sendAccountantInvite(accountantEmail,
                owner.getFirstName() + " " + owner.getLastName(), token, expiresAt);

        return AccountantAccessDto.fromFull(grant);
    }

    @Transactional
    public void revokeAccess(UUID ownerId, UUID grantId) {
        AccountantAccessEntity grant = accessRepo.findByIdOptional(grantId)
                .orElseThrow(() -> new NotFoundException("Grant not found"));
        if (!grant.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("Access denied");
        }
        accessRepo.delete(grant);
    }

    /**
     * Validate an accountant read-only token (used by the accountant portal endpoint).
     * Returns the owning user's ID if valid.
     */
    public UUID validateToken(String token) {
        AccountantAccessEntity grant = accessRepo.findByAccessToken(token)
                .orElseThrow(() -> new ForbiddenException("Token invalide ou expiré"));
        if (grant.isExpired()) {
            throw new ForbiddenException("Token expiré");
        }
        return grant.getOwner().getId();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
