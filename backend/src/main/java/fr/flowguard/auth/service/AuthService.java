package fr.flowguard.auth.service;

import fr.flowguard.auth.entity.EmailVerificationEntity;
import fr.flowguard.auth.entity.UserEntity;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    JWTParser jwtParser;

    @Inject
    Mailer mailer;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public RegisterPendingResult register(String email, String password,
                                          String firstName, String lastName) {
        if (UserEntity.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe deja avec cet email");
        }

        UserEntity user = new UserEntity();
        user.email = email;
        user.passwordHash = BcryptUtil.bcryptHash(password);
        user.firstName = firstName;
        user.lastName = lastName;
        user.persist();

        // Invalidate any previous unused OTPs for this address
        EmailVerificationEntity.delete("email = ?1 and used = false", email);

        String otp = generateOtp();
        EmailVerificationEntity verification = new EmailVerificationEntity();
        verification.email = email;
        verification.otpCode = otp;
        verification.expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        verification.persist();

        sendVerificationEmail(email, firstName, otp);
        LOG.infof("Nouveau compte cree, verification email envoyee : %s", email);

        return new RegisterPendingResult(true, maskEmail(email));
    }

    // ── Email verification (one-time) ─────────────────────────────────────────

    @Transactional
    public AuthResult verifyEmail(String email, String code) {
        EmailVerificationEntity verification =
                EmailVerificationEntity.findActiveByEmail(email)
                        .orElseThrow(() -> new SecurityException("Code invalide ou expire"));

        if (!verification.otpCode.equals(code)) {
            throw new SecurityException("Code invalide ou expire");
        }

        verification.used = true;

        UserEntity user = UserEntity.findByEmail(email)
                .orElseThrow(() -> new SecurityException("Utilisateur introuvable"));
        user.emailVerified = true;

        LOG.infof("Email verifie : %s", email);
        return buildAuthResult(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResult login(String email, String password) {
        UserEntity user = UserEntity.findByEmail(email)
                .filter(u -> u.isActive)
                .orElseThrow(() -> new SecurityException("Identifiants incorrects"));

        if (!BcryptUtil.matches(password, user.passwordHash)) {
            throw new SecurityException("Identifiants incorrects");
        }

        if (!user.emailVerified) {
            throw new EmailNotVerifiedException(maskEmail(email));
        }

        LOG.infof("Login reussi : %s", email);
        return buildAuthResult(user);
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    public AuthResult refresh(String refreshTokenStr) {
        try {
            JsonWebToken jwt = jwtParser.parse(refreshTokenStr);
            String userId = jwt.getSubject();
            UserEntity user = UserEntity.findById(userId);
            if (user == null || !user.isActive) {
                throw new SecurityException("Utilisateur introuvable");
            }
            return buildAuthResult(user);
        } catch (EmailNotVerifiedException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Refresh token invalide ou expire");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.charAt(at - 1) + email.substring(at);
    }

    private void sendVerificationEmail(String email, String firstName, String otp) {
        String body = String.format(
                "Bonjour %s,%n%n"
                + "Voici votre code de verification FlowGuard : %s%n%n"
                + "Ce code expire dans 15 minutes.%n%n"
                + "L'equipe FlowGuard",
                firstName, otp);
        mailer.send(Mail.withText(email, "Verifiez votre adresse email FlowGuard", body));
    }

    private AuthResult buildAuthResult(UserEntity user) {
        String accessToken = Jwt.issuer("https://flowguard.fr")
                .subject(user.id)
                .upn(user.email)
                .groups(Set.of(user.role))
                .claim("email", user.email)
                .claim("firstName", user.firstName)
                .claim("lastName", user.lastName)
                .claim("role", user.role)
                .expiresIn(Duration.ofHours(24))
                .sign();

        String refreshToken = Jwt.issuer("https://flowguard.fr")
                .subject(user.id)
                .upn(user.email)
                .groups(Set.of("refresh"))
                .expiresIn(Duration.ofDays(30))
                .sign();

        return new AuthResult(
                accessToken,
                refreshToken,
                new AuthResult.UserInfo(
                        user.id, user.email, user.firstName, user.lastName,
                        user.role, user.kycStatus, user.emailVerified
                )
        );
    }

    // ── Result / exception types ──────────────────────────────────────────────

    public record RegisterPendingResult(
            boolean pendingVerification,
            String maskedEmail
    ) {}

    public record AuthResult(
            String accessToken,
            String refreshToken,
            UserInfo user
    ) {
        public record UserInfo(
                String id,
                String email,
                String firstName,
                String lastName,
                String role,
                String kycStatus,
                boolean emailVerified
        ) {}
    }

    public static class EmailNotVerifiedException extends RuntimeException {
        public final String maskedEmail;
        public EmailNotVerifiedException(String maskedEmail) {
            super("Veuillez verifier votre email avant de vous connecter");
            this.maskedEmail = maskedEmail;
        }
    }
}