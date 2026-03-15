package com.flowguard.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;

/**
 * Scheduled job enforcing RGPD / Article 17 data retention policies.
 *
 * <p>Runs every Sunday at 02:00 to minimise load impact.
 *
 * <p>Retention periods (configurable via environment variables):
 * <ul>
 *   <li>Transactions     — 5 years (AML / LCB-FT obligation)
 *   <li>Audit logs       — 5 years (DSP2 traceability)
 *   <li>Predictions      — 6 months
 *   <li>Expired refresh tokens — purged after 90 days past expiry
 *   <li>Consent records  — kept until withdrawal + 5 years (DSP2 Article 94)
 *   <li>Flash credit apps — 5 years (crédit regulation)
 * </ul>
 */
@ApplicationScoped
public class DataRetentionScheduler {

    private static final Logger LOG = Logger.getLogger(DataRetentionScheduler.class);

    // Retention periods
    private static final int TRANSACTIONS_YEARS  = 5;
    private static final int AUDIT_LOG_YEARS     = 5;
    private static final int PREDICTIONS_MONTHS  = 6;
    private static final int FLASH_CREDIT_YEARS  = 5;
    private static final int REFRESH_TOKEN_DAYS  = 90; // days after expiry before purge

    @Inject
    jakarta.persistence.EntityManager em;

    /**
     * Main retention job — runs weekly on Sundays at 02:00.
     */
    @Scheduled(cron = "0 0 2 ? * SUN")
    @Transactional
    public void runRetention() {
        LOG.info("Starting RGPD data retention job");

        int txDeleted       = purgeOldTransactions();
        int auditDeleted    = purgeOldAuditLogs();
        int predDeleted     = purgeOldPredictions();
        int tokenDeleted    = purgeExpiredRefreshTokens();
        int creditDeleted   = purgeOldFlashCreditHistory();

        LOG.infof("Retention job complete — tx=%d audit=%d pred=%d tokens=%d credit=%d",
                txDeleted, auditDeleted, predDeleted, tokenDeleted, creditDeleted);
    }

    // ── Transaction data ───────────────────────────────────────────────────────

    private int purgeOldTransactions() {
        LocalDate cutoff = LocalDate.now().minusYears(TRANSACTIONS_YEARS);
        int n = em.createQuery("DELETE FROM TransactionEntity t WHERE t.date < :cutoff")
                  .setParameter("cutoff", cutoff)
                  .executeUpdate();
        if (n > 0) LOG.infof("Purged %d transactions older than %d years", n, TRANSACTIONS_YEARS);
        return n;
    }

    // ── Audit logs ─────────────────────────────────────────────────────────────

    private int purgeOldAuditLogs() {
        java.time.Instant cutoff = java.time.Instant.now()
                .minus(java.time.Duration.ofDays(AUDIT_LOG_YEARS * 365L));
        int n = em.createNativeQuery("DELETE FROM audit_log WHERE created_at < :cutoff")
                  .setParameter("cutoff",
                      java.sql.Timestamp.from(cutoff))
                  .executeUpdate();
        if (n > 0) LOG.infof("Purged %d audit log entries older than %d years", n, AUDIT_LOG_YEARS);
        return n;
    }

    // ── ML Predictions ─────────────────────────────────────────────────────────

    private int purgeOldPredictions() {
        java.time.Instant cutoff = java.time.Instant.now()
                .minus(java.time.Duration.ofDays(PREDICTIONS_MONTHS * 30L));
        int n = em.createNativeQuery("DELETE FROM predictions WHERE created_at < :cutoff")
                  .setParameter("cutoff", java.sql.Timestamp.from(cutoff))
                  .executeUpdate();
        if (n > 0) LOG.infof("Purged %d predictions older than %d months", n, PREDICTIONS_MONTHS);
        return n;
    }

    // ── Refresh tokens ─────────────────────────────────────────────────────────

    private int purgeExpiredRefreshTokens() {
        java.time.Instant cutoff = java.time.Instant.now()
                .minus(java.time.Duration.ofDays(REFRESH_TOKEN_DAYS));
        int n = em.createNativeQuery(
                "DELETE FROM refresh_tokens WHERE expires_at < :cutoff")
                 .setParameter("cutoff", java.sql.Timestamp.from(cutoff))
                 .executeUpdate();
        if (n > 0) LOG.infof("Purged %d expired refresh tokens", n);
        return n;
    }

    // ── Flash credit history ───────────────────────────────────────────────────

    private int purgeOldFlashCreditHistory() {
        java.time.Instant cutoff = java.time.Instant.now()
                .minus(java.time.Duration.ofDays(FLASH_CREDIT_YEARS * 365L));
        // Only delete fully repaid / cancelled credits (never delete active ones)
        int n = em.createNativeQuery(
                "DELETE FROM flash_credits WHERE status IN ('REPAID','CANCELLED') "
                + "AND created_at < :cutoff")
                 .setParameter("cutoff", java.sql.Timestamp.from(cutoff))
                 .executeUpdate();
        if (n > 0) LOG.infof("Purged %d old flash credit records", n);
        return n;
    }
}
