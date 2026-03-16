-- ==============================================
-- FlowGuard — V10 LCB-FT / AML Compliance Tables
-- Loi du 12 juillet 1990 (LCB-FT), Art. L561-12 CMF (5-year retention)
-- ==============================================

-- ── Sanctions Screening Audit Log ─────────────────────────────────────────────
-- Art. L561-5 CMF : Obligation de vérification de l'identité et contrôle
-- des listes de sanctions EU/OFAC/ONU à l'entrée en relation d'affaires.
-- Conservation : 5 ans (AMF Instruction 2019-07).

CREATE TABLE IF NOT EXISTS sanctions_screening_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    full_name     VARCHAR(255) NOT NULL,
    date_of_birth VARCHAR(20),
    hit_type      VARCHAR(20)  NOT NULL, -- NO_HIT | FUZZY_HIT | CONFIRMED_HIT
    match_score   NUMERIC(5,4),
    matched_entry VARCHAR(500),
    list_source   VARCHAR(50),           -- EU_CFSL | OFAC_SDN | UN_CONSOLIDATED
    screened_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sanctions_log_user     ON sanctions_screening_log(user_id);
CREATE INDEX idx_sanctions_log_hit_type ON sanctions_screening_log(hit_type) WHERE hit_type != 'NO_HIT';
CREATE INDEX idx_sanctions_log_date     ON sanctions_screening_log(screened_at);

-- Immutable audit trail — no UPDATE/DELETE allowed via application role
COMMENT ON TABLE sanctions_screening_log IS
    'LCB-FT sanctions screening audit log — Art. L561-5 CMF. Retention: 5 years.';

-- ── TRACFIN Suspicious Activity Reports ───────────────────────────────────────
-- Art. L561-15 CMF : Déclaration de soupçon auprès de TRACFIN.
-- Conservation : 5 ans (Art. L561-12 CMF).
-- AVERTISSEMENT : Ces données ne doivent JAMAIS être divulguées à la personne
-- concernée (délit de tipping-off, Art. L574-1 CMF).

CREATE TABLE IF NOT EXISTS tracfin_reports (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         REFERENCES users(id) ON DELETE SET NULL,
    user_full_name    VARCHAR(255) NOT NULL,
    user_email        VARCHAR(255) NOT NULL,
    user_company      VARCHAR(255),
    suspicion_type    VARCHAR(50)  NOT NULL,  -- SuspicionType enum
    narrative         TEXT         NOT NULL,
    trigger_amount    NUMERIC(15,2),
    status            VARCHAR(40)  NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewer_user_id  UUID         REFERENCES users(id) ON DELETE SET NULL,
    review_notes      TEXT,
    reviewed_at       TIMESTAMPTZ,
    ermes_decl_ref    VARCHAR(100),           -- TRACFIN ERMES portal reference
    submitted_at      TIMESTAMPTZ
);

CREATE INDEX idx_tracfin_status    ON tracfin_reports(status);
CREATE INDEX idx_tracfin_user      ON tracfin_reports(user_id);
CREATE INDEX idx_tracfin_created   ON tracfin_reports(created_at DESC);
CREATE INDEX idx_tracfin_open      ON tracfin_reports(user_id, suspicion_type)
    WHERE status IN ('PENDING_REVIEW', 'CONFIRMED_SUSPICION');

COMMENT ON TABLE tracfin_reports IS
    'TRACFIN suspicious activity reports — Art. L561-15 CMF. CONFIDENTIAL. 5-year retention.';
COMMENT ON COLUMN tracfin_reports.ermes_decl_ref IS
    'TRACFIN ERMES portal reference number entered after manual submission.';
