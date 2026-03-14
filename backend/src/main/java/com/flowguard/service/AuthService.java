package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AuthResponse;
import com.flowguard.dto.LoginRequest;
import com.flowguard.dto.RegisterRequest;
import com.flowguard.dto.UserDto;
import com.flowguard.repository.UserRepository;
import com.flowguard.security.RateLimiter;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    JWTParser jwtParser;

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

        return buildAuthResponse(user);
    }

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

        rateLimiter.reset(request.email());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String token) {
        try {
            var parsed = jwtParser.parse(token);
            String userId = parsed.getSubject();
            UserEntity user = userRepository.findById(java.util.UUID.fromString(userId));
            if (user == null || user.isDisabled()) {
                throw new SecurityException("Token invalide");
            }
            return buildAuthResponse(user);
        } catch (Exception e) {
            throw new SecurityException("Token invalide");
        }
    }

    public void logout(String refreshToken) {
        // Stateless JWT: nothing to invalidate server-side (refresh tokens are short-lived)
        // In production, add to a blocklist in Redis.
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

    private AuthResponse buildAuthResponse(UserEntity user) {
        String group = roleToGroup(user.getRole());

        String accessToken = Jwt.issuer("https://flowguard.fr")
                .subject(user.getId().toString())
                .upn(user.getEmail())
                .groups(Set.of(group))
                .claim("email", user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("role", user.getRole())
                .claim("companyName", user.getCompanyName() != null ? user.getCompanyName() : "")
                .claim("userType", user.getUserType() != null ? user.getUserType().name() : "")
                .expiresIn(Duration.ofHours(24))
                .sign();

        String refreshToken = Jwt.issuer("https://flowguard.fr")
                .subject(user.getId().toString())
                .upn(user.getEmail())
                .groups(Set.of("refresh"))
                .expiresIn(Duration.ofDays(30))
                .sign();

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
