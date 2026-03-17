package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AuthResponse;
import com.flowguard.dto.EmailVerificationPendingResponse;
import com.flowguard.dto.LoginRequest;
import com.flowguard.dto.MfaChallengeResponse;
import com.flowguard.dto.RegisterRequest;
import com.flowguard.dto.UserDto;
import com.flowguard.dto.VerifyEmailRequest;
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
    public EmailVerificationPendingResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }

        String passwordHash = BcryptUtil.bcryptHash(request.password());

        UserEntity user = UserEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordHash)
                .companyName(request.companyName() != null ? request.companyName() : "")
                .userType(request.userType())
                .kycStatus(UserEntity.KycStatus.PENDING)
                .emailVerified(false)
                .build();

        userRepository.persist(user);

        // LCB-FT Art. L561-5 CMF — screen against EU/OFAC/UN sanctions lists.
        sanctionsScreeningService.screenRegistration(
                user.getId(),
                request.firstName(),
                request.lastName(),
                null
        );

        // Send the one-time email verification OTP
        otpService.sendEmailVerificationOtp(user);

        return new EmailVerificationPendingResponse(OtpService.maskEmail(user.getEmail()));
    }

    /**
     * Verify the one-time e-mail confirmation OTP sent after registration.
     * Marks the account as verified and issues the initial token pair.
     */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        UserEntity user = otpService.verifyEmailCode(request.email(), request.code());
        user.setEmailVerified(true);
        String accessToken  = refreshTokenService.buildAccessToken(user);
        String refreshToken = refreshTokenService.issueRefreshToken(user, null, "email_verification");
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Validate credentials and issue tokens directly.
     * Email verification (one-time) is handled at registration via /verify-email.
     * After the account is verified, subsequent logins require only email + password.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        rateLimiter.checkAndRecord(request.email());

        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new SecurityException("Identifiants incorrects"));

        if (user.isDisabled()) {
            throw new SecurityException("Identifiants incorrects");
        }

        if (!BcryptUtil.matches(request.password(), user.getPasswordHash())) {
            throw new SecurityException("Identifiants incorrects");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(OtpService.maskEmail(user.getEmail()));
        }

        rateLimiter.reset(request.email());

        String accessToken  = refreshTokenService.buildAccessToken(user);
        String refreshToken = refreshTokenService.issueRefreshToken(user, null, "login");
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public static class EmailNotVerifiedException extends RuntimeException {
        public final String maskedEmail;
        public EmailNotVerifiedException(String maskedEmail) {
            super("EMAIL_NOT_VERIFIED");
            this.maskedEmail = maskedEmail;
        }
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

    private AuthResponse buildAuthResponse(UserEntity user, String accessToken, String refreshToken) {
        AuthResponse.UserResponse userResponse = new AuthResponse.UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCompanyName(),
                user.getUserType(),
                user.getKycStatus(),
                user.getRole(),
                user.isEmailVerified()
        );
        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

}
