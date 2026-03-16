package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AuthResponse;
import com.flowguard.dto.LoginRequest;
import com.flowguard.dto.MfaChallengeResponse;
import com.flowguard.dto.RegisterRequest;
import com.flowguard.dto.UserDto;
import com.flowguard.dto.VerifyOtpRequest;
import com.flowguard.repository.UserRepository;
import com.flowguard.security.RateLimiter;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    OtpService otpService;

    @Inject
    SanctionsScreeningService sanctionsScreeningService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }

        String passwordHash = BcryptUtil.bcryptHash(request.password());

        UserEntity user = UserEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordHash)
                .companyName(request.companyName())
                .userType(request.userType())
                .kycStatus(UserEntity.KycStatus.PENDING)
                .build();

        userRepository.persist(user);

        // LCB-FT Art. L561-5 CMF — screen against EU/OFAC/UN sanctions lists.
        // Runs after persist so the userId is available for audit logging.
        // SanctionsHitException is a RuntimeException → transaction rolls back automatically.
        sanctionsScreeningService.screenRegistration(
                user.getId(),
                request.firstName(),
                request.lastName(),
                null   // dateOfBirth not yet collected at registration; re-check at KYC
        );

        String accessToken = refreshTokenService.buildAccessToken(user);
        String refreshToken = refreshTokenService.issueRefreshToken(user, null, "registration");
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Step 1 — validate credentials and send an e-mail OTP.
     *
     * @return {@link MfaChallengeResponse} with the session token the client
     *         must present with the 6-digit code to {@code POST /auth/verify-otp}
     */
    public MfaChallengeResponse login(LoginRequest request) {
        rateLimiter.checkAndRecord(request.email());

        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new SecurityException("Identifiants incorrects"));

        if (user.isDisabled()) {
            throw new SecurityException("Identifiants incorrects");
        }

        if (!BcryptUtil.matches(request.password(), user.getPasswordHash())) {
            throw new SecurityException("Identifiants incorrects");
        }

        rateLimiter.reset(request.email());

        String sessionToken = otpService.sendOtp(user);
        return new MfaChallengeResponse(sessionToken, OtpService.maskEmail(user.getEmail()));
    }

    /**
     * Step 2 — verify the OTP and issue the final JWT + refresh-token pair.
     */
    @Transactional
    public AuthResponse completeLogin(VerifyOtpRequest request) {
        UserEntity user       = otpService.verify(request.sessionToken(), request.code());
        String accessToken    = refreshTokenService.buildAccessToken(user);
        String refreshToken   = refreshTokenService.issueRefreshToken(user, null, "login");
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.TokenPair pair = refreshTokenService.rotate(rawRefreshToken, null);
        return buildAuthResponse(pair.user(), pair.accessToken(), pair.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(rawRefreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String tokenHash = java.util.Base64.getEncoder().encodeToString(hash);
                refreshTokenService.revokeByHash(tokenHash);
            } catch (Exception e) {
                // Best-effort revocation; token will expire naturally
            }
        }
    }

    public UserDto getUser(String userId) {
        UserEntity user = userRepository.findById(java.util.UUID.fromString(userId));
        if (user == null) {
            throw new SecurityException("Utilisateur introuvable");
        }
        return UserDto.from(user);
    }

    /** Map stored role (e.g. ROLE_ADMIN) to the short Quarkus group name (e.g. admin). */
    private static String roleToGroup(String role) {
        if (role == null) return "user";
        return switch (role.toUpperCase()) {
            case "ROLE_ADMIN"        -> "admin";
            case "ROLE_SUPER_ADMIN"  -> "super_admin";
            case "ROLE_BUSINESS"     -> "business";
            default                 -> "user";
        };
    }

    private AuthResponse buildAuthResponse(UserEntity user, String accessToken, String refreshToken) {
        AuthResponse.UserResponse userResponse = new AuthResponse.UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCompanyName(),
                user.getUserType(),
                user.getKycStatus(),
                user.getRole()
        );
        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

}
