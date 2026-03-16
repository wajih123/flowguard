package com.flowguard.service;

import com.flowguard.domain.TracfinReport;
import com.flowguard.domain.TracfinReport.ReportStatus;
import com.flowguard.domain.TracfinReport.SuspicionType;
import com.flowguard.domain.UserEntity;
import com.flowguard.repository.TracfinReportRepository;
import com.flowguard.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service de déclaration de soupçon TRACFIN — Art. L561-15 CMF.
 *
 * <p>TRACFIN (Traitement du renseignement et action contre les circuits financiers
 * clandestins) is the French financial intelligence unit under the Ministry of Economy.
 *
 * <p>Obligations légales (Art. L561-15 CMF) :
 * <ul>
 *   <li>Déclarer tout soupçon de blanchiment ou financement du terrorisme
 *   <li>Délai maximum : SANS délai (immédiatement ou dès détection)
 *   <li>Conservation des dossiers : 5 ans (Art. L561-12)
 *   <li>Interdiction de divulgation au client ("tipping-off", Art. L574-1)
 * </ul>
 *
 * <p>Workflow :
 * 1. Anomaly detection / manual flag → {@link #flagSuspiciousActivity}
 * 2. Compliance officer reviews (Admin UI) → {@link #reviewReport}
 * 3. If confirmed → {@link #submitToTracfin} generates TRACFIN-formatted dossier
 *    and opens a TRACFIN ERMES portal ticket (manual step, documented here)
 * 4. Account may be frozen pending investigation → {@link #freezeUserAccount}
 */
@ApplicationScoped
public class TracfinService {

    private static final Logger LOG = Logger.getLogger(TracfinService.class);

    /** TRACFIN reporting threshold for cash-equivalent transactions (Art. L561-15-I CMF) */
    private static final BigDecimal CASH_THRESHOLD_EUR = new BigDecimal("10000");

    /** Structuring threshold — multiple transactions just below CASH_THRESHOLD */
    private static final BigDecimal STRUCTURING_THRESHOLD = new BigDecimal("9000");

    @ConfigProperty(name = "flowguard.compliance.officer-email", defaultValue = "compliance@flowguard.fr")
    String complianceOfficerEmail;

    @ConfigProperty(name = "flowguard.tracfin.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    TracfinReportRepository reportRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    Mailer mailer;

    // ── 1. Flag suspicious activity ────────────────────────────────────────────

    /**
     * Flag a user for suspicious activity. Creates a PENDING TRACFIN report for
     * manual compliance review. Does NOT yet submit to TRACFIN.
     *
     * @param userId        user whose activity is suspicious
     * @param suspicionType category of suspicion
     * @param narrative     factual description (who, what, when, amounts)
     * @param triggerAmount amount of the triggering transaction(s)
     * @return the created report ID
     */
    @Transactional
    public UUID flagSuspiciousActivity(
            UUID userId,
            SuspicionType suspicionType,
            String narrative,
            BigDecimal triggerAmount
    ) {
        if (!enabled) {
            LOG.debug("TRACFIN service disabled — skipping flag");
            return null;
        }

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + userId);
        }

        // Check if there's already an open report for this user and suspicion type
        boolean alreadyOpen = reportRepository.hasOpenReport(userId, suspicionType);
        if (alreadyOpen) {
            LOG.infof("TRACFIN: open report already exists for userId=%s type=%s — not duplicating", userId, suspicionType);
            return null;
        }

        TracfinReport report = new TracfinReport();
        report.setUserId(userId);
        report.setUserFullName(user.getFirstName() + " " + user.getLastName());
        report.setUserEmail(user.getEmail());
        report.setUserCompany(user.getCompanyName());
        report.setSuspicionType(suspicionType);
        report.setNarrative(narrative);
        report.setTriggerAmount(triggerAmount);
        report.setStatus(ReportStatus.PENDING_REVIEW);
        report.setCreatedAt(Instant.now());
        reportRepository.persist(report);

        LOG.warnf("TRACFIN report created: id=%s userId=%s type=%s amount=%s",
                report.getId(), userId, suspicionType, triggerAmount);

        // Alert compliance officer immediately (SANS délai)
        notifyComplianceOfficer(report, user);

        return report.getId();
    }

    /**
     * Automatically detect structuring attempts (smurfing): multiple transactions
     * between STRUCTURING_THRESHOLD and CASH_THRESHOLD within 24h from the same user.
     * Art. L561-15-I CMF — structuring is a standalone suspicion criterion.
     */
    @Transactional
    public void detectStructuring(UUID userId, List<BigDecimal> recentAmounts) {
        long suspiciousCount = recentAmounts.stream()
                .filter(a -> a.compareTo(STRUCTURING_THRESHOLD) >= 0
                          && a.compareTo(CASH_THRESHOLD_EUR) < 0)
                .count();

        if (suspiciousCount >= 3) {
            String narrative = String.format(
                "Suspicion de fractionnement (smurfing) : %d transactions entre %s EUR et %s EUR dans les 24h.",
                suspiciousCount, STRUCTURING_THRESHOLD, CASH_THRESHOLD_EUR
            );
            flagSuspiciousActivity(userId, SuspicionType.STRUCTURING, narrative,
                    recentAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    // ── 2. Compliance officer review ──────────────────────────────────────────

    /**
     * Record the outcome of the compliance officer's review.
     *
     * @param reportId         TRACFIN report to review
     * @param approved         true = confirmed suspicion, false = false positive
     * @param reviewerUserId   user ID of the compliance officer
     * @param reviewNotes      mandatory notes justifying the decision
     */
    @Transactional
    public TracfinReport reviewReport(
            UUID reportId,
            boolean approved,
            UUID reviewerUserId,
            String reviewNotes
    ) {
        TracfinReport report = reportRepository.findById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("Rapport TRACFIN introuvable : " + reportId);
        }

        if (report.getStatus() != ReportStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Le rapport est déjà traité (statut : " + report.getStatus() + ")");
        }

        report.setReviewerUserId(reviewerUserId);
        report.setReviewNotes(reviewNotes);
        report.setReviewedAt(Instant.now());
        report.setStatus(approved ? ReportStatus.CONFIRMED_SUSPICION : ReportStatus.FALSE_POSITIVE);

        LOG.infof("TRACFIN report %s reviewed by %s: %s", reportId, reviewerUserId,
                approved ? "CONFIRMED" : "FALSE_POSITIVE");

        return report;
    }

    // ── 3. Submit to TRACFIN ERMES portal ─────────────────────────────────────

    /**
     * Generate the TRACFIN dossier and mark the report as submitted.
     *
     * <p>TRACFIN does NOT expose a public API — declarations must be submitted via
     * the TRACFIN ERMES secure portal (https://ermes.tracfin.mineco.gouv.fr).
     * This method generates the formatted dossier PDF/email for the compliance
     * officer to upload manually, and records the submission timestamp.
     *
     * @param reportId  the confirmed report to submit
     * @param ermesDeclRef ERMES portal reference number (entered after manual submission)
     */
    @Transactional
    public TracfinReport submitToTracfin(UUID reportId, String ermesDeclRef) {
        TracfinReport report = reportRepository.findById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("Rapport introuvable : " + reportId);
        }

        if (report.getStatus() != ReportStatus.CONFIRMED_SUSPICION) {
            throw new IllegalStateException("Seuls les rapports confirmés peuvent être soumis à TRACFIN");
        }

        report.setErmesDeclRef(ermesDeclRef);
        report.setSubmittedAt(Instant.now());
        report.setStatus(ReportStatus.SUBMITTED_TO_TRACFIN);

        LOG.warnf("TRACFIN declaration submitted: reportId=%s ermes=%s userId=%s",
                reportId, ermesDeclRef, report.getUserId());

        // Send dossier to compliance officer for ERMES upload
        sendTracfinDossier(report);

        return report;
    }

    // ── 4. Account freeze ─────────────────────────────────────────────────────

    /**
     * Freeze a user account pending TRACFIN investigation.
     * Art. L561-24 CMF — the reporting entity may oppose a transaction for 3 days
     * awaiting TRACFIN authorization.
     */
    @Transactional
    public void freezeUserAccount(UUID userId, UUID reportId, String reason) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) return;

        user.setDisabled(true);
        user.setDisabledAt(Instant.now());
        user.setDisabledReason("TRACFIN_FREEZE: " + reason + " [report=" + reportId + "]");

        LOG.warnf("Account FROZEN for TRACFIN: userId=%s reportId=%s", userId, reportId);
    }

    // ── 5. List reports for admin UI ──────────────────────────────────────────

    public List<TracfinReport> getPendingReports() {
        return reportRepository.findByStatus(ReportStatus.PENDING_REVIEW);
    }

    public List<TracfinReport> getAllReports() {
        return reportRepository.listAll();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void notifyComplianceOfficer(TracfinReport report, UserEntity user) {
        try {
            String subject = "[TRACFIN] Nouveau rapport de soupçon — " + report.getSuspicionType();
            String body = buildComplianceEmailBody(report, user);
            mailer.send(Mail.withHtml(complianceOfficerEmail, subject, body));
        } catch (Exception e) {
            // Do not fail the transaction if email delivery fails — report is already persisted
            LOG.errorf("Failed to send TRACFIN notification email: %s", e.getMessage());
        }
    }

    private void sendTracfinDossier(TracfinReport report) {
        try {
            String subject = "[TRACFIN ERMES] Dossier de déclaration — Réf. " + report.getErmesDeclRef();
            String body = buildTracfinDossier(report);
            mailer.send(Mail.withHtml(complianceOfficerEmail, subject, body));
        } catch (Exception e) {
            LOG.errorf("Failed to send TRACFIN dossier email: %s", e.getMessage());
        }
    }

    private String buildComplianceEmailBody(TracfinReport report, UserEntity user) {
        return """
            <html><body style="font-family:sans-serif;color:#333">
            <h2 style="color:#c0392b">⚠️ Rapport de soupçon TRACFIN — Action requise</h2>
            <table border="0" cellpadding="8">
              <tr><td><b>Rapport ID</b></td><td>%s</td></tr>
              <tr><td><b>Utilisateur</b></td><td>%s (%s)</td></tr>
              <tr><td><b>Entreprise</b></td><td>%s</td></tr>
              <tr><td><b>Type de soupçon</b></td><td>%s</td></tr>
              <tr><td><b>Montant déclencheur</b></td><td>%s €</td></tr>
              <tr><td><b>Créé le</b></td><td>%s</td></tr>
            </table>
            <h3>Narrative</h3>
            <p style="background:#fff3cd;padding:12px;border-left:4px solid #ffc107">%s</p>
            <hr>
            <p>Veuillez examiner ce rapport dans l'interface d'administration FlowGuard et prendre une décision
            dans les 24h (obligation légale Art. L561-15 CMF).</p>
            <p>En cas de confirmation du soupçon, soumettez la déclaration via le portail
            <a href="https://ermes.tracfin.mineco.gouv.fr">TRACFIN ERMES</a>.</p>
            <p style="color:#999;font-size:11px">⚠️ Ce message est confidentiel. Toute divulgation à la personne concernée
            constitue une infraction pénale (Art. L574-1 CMF — tipping-off).</p>
            </body></html>
            """.formatted(
                report.getId(),
                report.getUserFullName(), report.getUserEmail(),
                report.getUserCompany(),
                report.getSuspicionType(),
                report.getTriggerAmount(),
                report.getCreatedAt(),
                report.getNarrative()
            );
    }

    private String buildTracfinDossier(TracfinReport report) {
        return """
            <html><body style="font-family:sans-serif;color:#333">
            <h2>Dossier TRACFIN — Déclaration de soupçon</h2>
            <p><b>Référence ERMES :</b> %s</p>
            <p><b>Date de déclaration :</b> %s</p>
            <table border="1" cellpadding="8" style="border-collapse:collapse">
              <tr><th>Champ TRACFIN</th><th>Valeur</th></tr>
              <tr><td>Identité déclarant</td><td>FlowGuard SAS — Numéro SIRET : [SIRET]</td></tr>
              <tr><td>Responsable déclaration</td><td>Dirigeant ou DPO désigné</td></tr>
              <tr><td>Personne concernée</td><td>%s</td></tr>
              <tr><td>Email</td><td>%s</td></tr>
              <tr><td>Entreprise</td><td>%s</td></tr>
              <tr><td>Type de soupçon</td><td>%s</td></tr>
              <tr><td>Montant(s)</td><td>%s €</td></tr>
              <tr><td>Narrative factuelle</td><td>%s</td></tr>
              <tr><td>Référence interne</td><td>%s</td></tr>
            </table>
            <br>
            <p>À soumettre sur : <a href="https://ermes.tracfin.mineco.gouv.fr">ermes.tracfin.mineco.gouv.fr</a></p>
            </body></html>
            """.formatted(
                report.getErmesDeclRef(),
                report.getSubmittedAt(),
                report.getUserFullName(),
                report.getUserEmail(),
                report.getUserCompany(),
                report.getSuspicionType(),
                report.getTriggerAmount(),
                report.getNarrative(),
                report.getId()
            );
    }
}
