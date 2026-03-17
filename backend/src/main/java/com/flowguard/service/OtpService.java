package com.flowguard.service;

import com.flowguard.cache.RedisCacheService;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Handles e-mail OTP generation, delivery and verification for the 2-step
 * login flow.
 *
 * <p>Redis keyspace used (all keys expire after {@value #OTP_TTL_SECONDS}s):
 * <ul>
 *   <li>{@code otp_code:{userId}}      – the 6-digit code (plain text)</li>
 *   <li>{@code otp_session:{token}}    – maps opaque session token → userId</li>
 *   <li>{@code otp_attempts:{userId}}  – failed-attempt counter</li>
 * </ul>
 */
@ApplicationScoped
public class OtpService {

    static final int    OTP_TTL_SECONDS = 600;   // 10 minutes
    static final int    EMAIL_VERIFY_TTL_SECONDS = 900; // 15 minutes
    private static final int    MAX_ATTEMPTS    = 5;
    private static final String CODE_PREFIX              = "otp_code:";
    private static final String SESSION_PREFIX           = "otp_session:";
    private static final String ATTEMPTS_PREFIX          = "otp_attempts:";
    private static final String EMAIL_VERIFY_CODE_PREFIX = "email_verify_code:";
    private static final String EMAIL_VERIFY_ATT_PREFIX  = "email_verify_attempts:";

    @Inject RedisCacheService redis;
    @Inject Mailer            mailer;
    @Inject UserRepository    userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Generate a 6-digit OTP, persist it in Redis, and e-mail it to the user.
     *
     * @return an opaque 64-hex-char session token the client must send back
     *         with the code to {@code POST /auth/verify-otp}
     */
    public String sendOtp(UserEntity user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String sessionToken = HexFormat.of().formatHex(bytes);

        redis.set(CODE_PREFIX    + user.getId(),   code,                   OTP_TTL_SECONDS);
        redis.set(SESSION_PREFIX + sessionToken,   user.getId().toString(), OTP_TTL_SECONDS);

        sendEmail(user.getEmail(), user.getFirstName(), code);
        return sessionToken;
    }

    /**
     * Verify an OTP.  On success all related Redis keys are deleted and the
     * matched {@link UserEntity} is returned; on failure a descriptive
     * {@link SecurityException} is thrown.
     */
    public UserEntity verify(String sessionToken, String code) {
        String userId = redis.get(SESSION_PREFIX + sessionToken);
        if (userId == null) {
            throw new SecurityException("Session expirée ou invalide. Veuillez recommencer la connexion.");
        }

        // Rate-limit failed attempts per user
        String attemptsKey = ATTEMPTS_PREFIX + userId;
        int attempts = parseAttempts(redis.get(attemptsKey));
        if (attempts >= MAX_ATTEMPTS) {
            invalidateAll(sessionToken, userId, attemptsKey);
            throw new SecurityException("Trop de tentatives incorrectes. Veuillez recommencer la connexion.");
        }

        String storedCode = redis.get(CODE_PREFIX + userId);
        if (storedCode == null) {
            throw new SecurityException("Code expiré. Veuillez recommencer la connexion.");
        }

        if (!constantTimeEquals(storedCode, code)) {
            int remaining = MAX_ATTEMPTS - attempts - 1;
            redis.set(attemptsKey, String.valueOf(attempts + 1), OTP_TTL_SECONDS);
            throw new SecurityException(
                    "Code incorrect. " + remaining + " tentative(s) restante(s).");
        }

        // Success — clean up
        invalidateAll(sessionToken, userId, attemptsKey);

        UserEntity user = userRepository.findById(UUID.fromString(userId));
        if (user == null || user.isDisabled()) {
            throw new SecurityException("Compte introuvable ou désactivé.");
        }
        return user;
    }

    /**
     * Generate a 6-digit OTP for one-time e-mail address verification at registration.
     * Uses a separate Redis keyspace from login OTPs (15-minute TTL).
     */
    public void sendEmailVerificationOtp(UserEntity user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redis.set(EMAIL_VERIFY_CODE_PREFIX + user.getId(), code, EMAIL_VERIFY_TTL_SECONDS);
        sendVerificationEmail(user.getEmail(), user.getFirstName(), code);
    }

    /**
     * Verify the e-mail confirmation OTP. On success returns the matched user.
     */
    public UserEntity verifyEmailCode(String email, String code) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("Compte introuvable."));

        String codeKey    = EMAIL_VERIFY_CODE_PREFIX + user.getId();
        String attemptsKey = EMAIL_VERIFY_ATT_PREFIX + user.getId();

        int attempts = parseAttempts(redis.get(attemptsKey));
        if (attempts >= MAX_ATTEMPTS) {
            redis.delete(codeKey);
            redis.delete(attemptsKey);
            throw new SecurityException("Trop de tentatives incorrectes. Veuillez recommencer l'inscription.");
        }

        String storedCode = redis.get(codeKey);
        if (storedCode == null) {
            throw new SecurityException("Code expiré. Veuillez demander un nouveau code de vérification.");
        }

        if (!constantTimeEquals(storedCode, code)) {
            int remaining = MAX_ATTEMPTS - attempts - 1;
            redis.set(attemptsKey, String.valueOf(attempts + 1), EMAIL_VERIFY_TTL_SECONDS);
            throw new SecurityException("Code incorrect. " + remaining + " tentative(s) restante(s).");
        }

        redis.delete(codeKey);
        redis.delete(attemptsKey);
        return user;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        int visible = Math.min(2, at);
        return email.substring(0, visible)
                + "*".repeat(at - visible)
                + email.substring(at);
    }

    private void invalidateAll(String sessionToken, String userId, String attemptsKey) {
        redis.delete(SESSION_PREFIX  + sessionToken);
        redis.delete(CODE_PREFIX     + userId);
        redis.delete(attemptsKey);
    }

    private static int parseAttempts(String val) {
        if (val == null) return 0;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    /** Constant-time comparison to prevent timing-based code enumeration. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private void sendEmail(String to, String firstName, String code) {
        String html = buildHtml(firstName, code);
        mailer.send(Mail.withHtml(to, "Votre code de vérification FlowGuard", html));
    }

    private void sendVerificationEmail(String to, String firstName, String code) {
        String html = buildVerificationHtml(firstName, code);
        mailer.send(Mail.withHtml(to, "Confirmez votre adresse e-mail FlowGuard", html));
    }

    private static String buildVerificationHtml(String firstName, String code) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Confirmez votre e-mail FlowGuard</title>
                </head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0d0d14;margin:0;padding:32px 16px;">
                  <div style="max-width:480px;margin:0 auto;background:#141421;border-radius:16px;padding:40px;">
                    <div style="color:#6C63FF;font-size:24px;font-weight:800;margin-bottom:32px;">⚡ FlowGuard</div>
                    <div style="color:#ffffff;font-size:22px;font-weight:700;margin-bottom:8px;">Bienvenue %s 🎉</div>
                    <div style="color:#8b8ba7;font-size:15px;margin-bottom:32px;">
                      Confirmez votre adresse e-mail pour activer votre compte&nbsp;:
                    </div>
                    <div style="background:#1e1e2e;border:2px solid #6C63FF;border-radius:12px;padding:24px 32px;text-align:center;margin-bottom:24px;">
                      <div style="color:#6C63FF;font-size:40px;font-weight:800;letter-spacing:12px;">%s</div>
                    </div>
                    <div style="color:#8b8ba7;font-size:13px;margin-bottom:24px;">
                      ⏱ Ce code expire dans <strong style="color:#ffffff;">15&nbsp;minutes</strong>.
                      Cette vérification n'est requise qu'une seule fois.
                    </div>
                    <p style="color:#8b8ba7;font-size:14px;">
                      Si vous n'avez pas créé ce compte, ignorez cet e-mail ou
                      <a href="https://flowguard.fr/support" style="color:#6C63FF;">contactez notre support</a>.
                    </p>
                    <div style="color:#4a4a6a;font-size:12px;text-align:center;margin-top:32px;border-top:1px solid #2a2a3e;padding-top:16px;">
                      &copy; 2026 FlowGuard SAS — Tous droits réservés
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(firstName, code);
    }

    private static String buildHtml(String firstName, String code) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Votre code FlowGuard</title>
                </head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0d0d14;margin:0;padding:32px 16px;">
                  <div style="max-width:480px;margin:0 auto;background:#141421;border-radius:16px;padding:40px;">
                    <div style="color:#6C63FF;font-size:24px;font-weight:800;margin-bottom:32px;">⚡ FlowGuard</div>
                    <div style="color:#ffffff;font-size:22px;font-weight:700;margin-bottom:8px;">Bonjour %s 👋</div>
                    <div style="color:#8b8ba7;font-size:15px;margin-bottom:32px;">
                      Entrez ce code pour finaliser votre connexion&nbsp;:
                    </div>
                    <div style="background:#1e1e2e;border:2px solid #6C63FF;border-radius:12px;padding:24px 32px;text-align:center;margin-bottom:24px;">
                      <div style="color:#6C63FF;font-size:40px;font-weight:800;letter-spacing:12px;">%s</div>
                    </div>
                    <div style="color:#8b8ba7;font-size:13px;margin-bottom:24px;">
                      ⏱ Ce code expire dans <strong style="color:#ffffff;">10&nbsp;minutes</strong>.
                      Ne le partagez jamais avec personne.
                    </div>
                    <p style="color:#8b8ba7;font-size:14px;">
                      Si vous n'avez pas demandé ce code, ignorez cet e-mail ou
                      <a href="https://flowguard.fr/support" style="color:#6C63FF;">contactez notre support</a>.
                    </p>
                    <div style="color:#4a4a6a;font-size:12px;text-align:center;margin-top:32px;border-top:1px solid #2a2a3e;padding-top:16px;">
                      © 2026 FlowGuard SAS — Tous droits réservés
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(firstName, code);
    }
}
