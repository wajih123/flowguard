package com.flowguard.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter en mémoire pour protéger contre le brute-force.
 * Stratégie : max N tentatives par fenêtre de temps, par clé (ex: email/IP).
 *
 * En production, utiliser Redis pour un rate-limiting distribué.
 */
@ApplicationScoped
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 900; // 15 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * Vérifie si la clé est bloquée, et si non, enregistre une tentative.
     *
     * @param key identifiant (email pour le login)
     * @throws SecurityException si le rate-limit est dépassé
     */
    public void checkAndRecord(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new AttemptRecord(now, 1);
            }
            if (existing.count >= MAX_ATTEMPTS) {
                long remainingSeconds = WINDOW_SECONDS -
                        (now.getEpochSecond() - existing.windowStart.getEpochSecond());
                throw new SecurityException(
                        "Trop de tentatives. Réessayez dans " + (remainingSeconds / 60 + 1) + " minutes.");
            }
            return new AttemptRecord(existing.windowStart, existing.count + 1);
        });
    }

    /**
     * Réinitialise le compteur après un login réussi.
     */
    public void reset(String key) {
        attempts.remove(key);
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
