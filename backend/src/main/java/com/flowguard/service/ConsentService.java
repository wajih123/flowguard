package com.flowguard.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * DSP2 / PSD2 Consent lifecycle management.
 *
 * <p>Bridge API renews consent every 90 days. This service:
 * <ol>
 *   <li>Records consent grants and revocations with an immutable audit trail.
 *   <li>Sends a reminder email 7 days before any consent expires.
 *   <li>Automatically marks Bridge accounts as {@code SUSPENDED} after expiry.
 * </ol>
 */
@ApplicationScoped
public class ConsentService {

    private static final Logger LOG = Logger.getLogger(ConsentService.class);

    /** DSP2: consent valid for 90 days maximum. */
    private static final int CONSENT_TTL_DAYS = 90;

    /** Reminder: 7 days before expiry. */
    private static final int REMINDER_DAYS_BEFORE = 7;

    @Inject
    EntityManager em;

    @Inject
    Mailer mailer;

    // ── Record consent ─────────────────────────────────────────────────────────

    /**
     * Records a new Bridge consent grant.
     *
     * @param userId    the FlowGuard user ID
     * @param accountId the FlowGuard account ID (may be null for user-level consent)
     * @param provider  e.g. "bridge", "nordigen"
     * @param scope     e.g. "accounts:read transactions:read"
     * @return the computed expiry instant (90 days from now)
     */
    @Transactional
    public Instant recordConsent(UUID userId, UUID accountId, String provider, String scope) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CONSENT_TTL_DAYS, ChronoUnit.DAYS);

        // Revoke any previous active consent for same user+provider+accountId
        em.createNativeQuery("""
            UPDATE consent_records
               SET revoked_at = :now
             WHERE user_id = :userId
               AND provider = :provider
               AND (account_id = :accountId OR (:accountId IS NULL AND account_id IS NULL))
               AND revoked_at IS NULL
            """)
          .setParameter("now", java.sql.Timestamp.from(now))
          .setParameter("userId", userId)
          .setParameter("provider", provider)
          .setParameter("accountId", accountId)
          .executeUpdate();

        em.createNativeQuery("""
            INSERT INTO consent_records
                (id, user_id, account_id, provider, scope, granted_at, expires_at)
            VALUES (gen_random_uuid(), :userId, :accountId, :provider, :scope, :now, :expiresAt)
            """)
          .setParameter("userId", userId)
          .setParameter("accountId", accountId)
          .setParameter("provider", provider)
          .setParameter("scope", scope)
          .setParameter("now", java.sql.Timestamp.from(now))
          .setParameter("expiresAt", java.sql.Timestamp.from(expiresAt))
          .executeUpdate();

        LOG.infof("Recorded consent for user=%s provider=%s expires=%s", userId, provider, expiresAt);
        return expiresAt;
    }

    /**
     * Revokes a user's consent for a given provider (right to withdraw — RGPD Art. 7.3).
     */
    @Transactional
    public void revokeConsent(UUID userId, String provider) {
        Instant now = Instant.now();
        int n = em.createNativeQuery("""
            UPDATE consent_records
               SET revoked_at = :now
             WHERE user_id = :userId
               AND provider = :provider
               AND revoked_at IS NULL
            """)
          .setParameter("now", java.sql.Timestamp.from(now))
          .setParameter("userId", userId)
          .setParameter("provider", provider)
          .executeUpdate();

        LOG.infof("Revoked %d consent(s) for user=%s provider=%s", n, userId, provider);
    }

    /**
     * Returns the active consent expiry for a user+provider, or empty if no active consent.
     */
    public java.util.Optional<Instant> getActiveConsentExpiry(UUID userId, String provider) {
        List<?> result = em.createNativeQuery("""
            SELECT expires_at FROM consent_records
             WHERE user_id = :userId
               AND provider = :provider
               AND revoked_at IS NULL
               AND expires_at > NOW()
             ORDER BY expires_at DESC
             LIMIT 1
            """)
          .setParameter("userId", userId)
          .setParameter("provider", provider)
          .getResultList();

        if (result.isEmpty()) return java.util.Optional.empty();
        java.sql.Timestamp ts = (java.sql.Timestamp) result.get(0);
        return java.util.Optional.of(ts.toInstant());
    }

    // ── Expiry checks ──────────────────────────────────────────────────────────

    /**
     * Scheduled: runs daily at 09:00 to send reminder emails and suspend expired accounts.
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void checkConsentExpiry() {
        Instant now = Instant.now();
        Instant reminderThreshold = now.plus(REMINDER_DAYS_BEFORE, ChronoUnit.DAYS);

        sendExpiryReminders(now, reminderThreshold);
        suspendExpiredAccounts(now);
    }

    @SuppressWarnings("unchecked")
    private void sendExpiryReminders(Instant now, Instant threshold) {
        List<Object[]> expiring = em.createNativeQuery("""
            SELECT cr.user_id, u.email, u.first_name, cr.expires_at
              FROM consent_records cr
              JOIN users u ON u.id = cr.user_id
             WHERE cr.revoked_at IS NULL
               AND cr.expires_at > :now
               AND cr.expires_at <= :threshold
               AND NOT EXISTS (
                 SELECT 1 FROM consent_records cr2
                  WHERE cr2.user_id = cr.user_id
                    AND cr2.granted_at > cr.granted_at
               )
            """)
          .setParameter("now", java.sql.Timestamp.from(now))
          .setParameter("threshold", java.sql.Timestamp.from(threshold))
          .getResultList();

        for (Object[] row : expiring) {
            String email     = (String) row[1];
            String firstName = (String) row[2];
            java.sql.Timestamp expiresAt = (java.sql.Timestamp) row[3];

            try {
                mailer.send(Mail.withHtml(email,
                    "⚠️ Votre connexion bancaire expire bientôt — FlowGuard",
                    buildReminderHtml(firstName, expiresAt.toInstant())));
                LOG.infof("Sent consent expiry reminder to %s (expires %s)", email, expiresAt);
            } catch (Exception e) {
                LOG.warnf("Failed to send consent reminder to %s: %s", email, e.getMessage());
            }
        }
    }

    private void suspendExpiredAccounts(Instant now) {
        int n = em.createNativeQuery("""
            UPDATE accounts a
               SET sync_status = 'ERROR', updated_at = NOW()
             WHERE a.id IN (
               SELECT DISTINCT cr.account_id
                 FROM consent_records cr
                WHERE cr.revoked_at IS NULL
                  AND cr.expires_at < :now
                  AND cr.account_id IS NOT NULL
             )
            """)
          .setParameter("now", java.sql.Timestamp.from(now))
          .executeUpdate();

        if (n > 0) {
            LOG.warnf("Suspended %d accounts with expired Bridge consent", n);
        }
    }

    private String buildReminderHtml(String firstName, Instant expiresAt) {
        long daysLeft = ChronoUnit.DAYS.between(Instant.now(), expiresAt);
        return """
            <html><body>
            <p>Bonjour %s,</p>
            <p>Votre autorisation de connexion bancaire via FlowGuard expire dans <strong>%d jours</strong>.</p>
            <p>Pour continuer à bénéficier des analyses en temps réel, veuillez
            <a href="https://app.flowguard.fr/settings/banking">renouveler votre connexion</a>.</p>
            <p>Si vous avez des questions, contactez notre support à <a href="mailto:support@flowguard.fr">support@flowguard.fr</a>.</p>
            <p>— L'équipe FlowGuard</p>
            </body></html>
            """.formatted(firstName, daysLeft);
    }
}
