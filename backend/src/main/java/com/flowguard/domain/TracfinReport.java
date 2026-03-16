package com.flowguard.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * TRACFIN suspicious activity report — Art. L561-15 CMF.
 * Retention: 5 years from creation (Art. L561-12 CMF).
 *
 * WARNING: These records must NEVER be disclosed to the subject (tipping-off
 * is a criminal offense under Art. L574-1 CMF — up to 1 year imprisonment).
 */
@Entity
@Table(name = "tracfin_reports")
@Getter
@Setter
@NoArgsConstructor
public class TracfinReport extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_full_name", nullable = false, length = 255)
    private String userFullName;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "user_company", length = 255)
    private String userCompany;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspicion_type", nullable = false, length = 50)
    private SuspicionType suspicionType;

    /** Factual narrative describing the suspicious behaviour */
    @Column(name = "narrative", nullable = false, length = 4000)
    private String narrative;

    @Column(name = "trigger_amount", precision = 15, scale = 2)
    private BigDecimal triggerAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReportStatus status;

    /** Compliance officer who reviewed the report */
    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;

    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** TRACFIN ERMES portal reference number after manual submission */
    @Column(name = "ermes_decl_ref", length = 100)
    private String ermesDeclRef;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum SuspicionType {
        /** Cash >10 000 EUR equivalent (Art. L561-15-I) */
        LARGE_CASH_TRANSACTION,
        /** Multiple transactions just below 10 000 EUR threshold */
        STRUCTURING,
        /** Sudden change in transaction patterns */
        UNUSUAL_PATTERN,
        /** Transactions to/from high-risk jurisdictions (GAFI list) */
        HIGH_RISK_JURISDICTION,
        /** PEP (Politically Exposed Person) indicator */
        PEP_INDICATOR,
        /** Account used for pass-through with no clear business purpose */
        PASS_THROUGH,
        /** Flash Credit misuse / rapid cycling */
        CREDIT_MISUSE,
        /** Terrorism financing indicators */
        TERRORISM_FINANCING,
        /** Manually flagged by compliance officer */
        MANUAL_FLAG
    }

    public enum ReportStatus {
        /** Awaiting compliance officer review */
        PENDING_REVIEW,
        /** Confirmed as genuine suspicious activity */
        CONFIRMED_SUSPICION,
        /** Determined to be a false positive */
        FALSE_POSITIVE,
        /** Submitted to TRACFIN ERMES portal */
        SUBMITTED_TO_TRACFIN,
        /** TRACFIN responded / case closed */
        CLOSED
    }
}
