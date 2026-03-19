-- V7: Financial Decision Engine
-- cash_risk_snapshots: point-in-time risk assessment stored for audit
CREATE TABLE IF NOT EXISTS cash_risk_snapshots (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    computed_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    risk_level      VARCHAR(10)    NOT NULL, -- LOW|MEDIUM|HIGH|CRITICAL
    runway_days     INTEGER,                 -- days until cash < threshold
    min_balance     NUMERIC(18,2),           -- min projected balance in horizon
    min_balance_date DATE,
    current_balance NUMERIC(18,2),
    volatility_score NUMERIC(5,4),          -- stddev / mean (coefficient of variation)
    deficit_predicted BOOLEAN     NOT NULL DEFAULT FALSE,
    score_version   VARCHAR(10)   NOT NULL DEFAULT 'v1'
);
CREATE INDEX idx_risk_user_date ON cash_risk_snapshots(user_id, computed_at DESC);

-- cash_drivers: top factors explaining the forecast (audit log)
CREATE TABLE IF NOT EXISTS cash_drivers (
    id              VARCHAR(36)    PRIMARY KEY,
    snapshot_id     VARCHAR(36)    NOT NULL REFERENCES cash_risk_snapshots(id) ON DELETE CASCADE,
    user_id         VARCHAR(36)    NOT NULL,
    driver_type     VARCHAR(40)    NOT NULL, -- TAX_PAYMENT|LATE_INVOICE|RECURRING_COST|PAYROLL|SUPPLIER|REVENUE_DROP
    label           TEXT           NOT NULL,
    amount          NUMERIC(18,2),
    impact_days     INTEGER,                 -- days within which this impacts cash
    due_date        DATE,
    reference_id    VARCHAR(36),             -- FK to invoice/tax/etc
    reference_type  VARCHAR(20),             -- INVOICE|TAX|TRANSACTION
    rank            SMALLINT       NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_driver_snapshot ON cash_drivers(snapshot_id);
CREATE INDEX idx_driver_user     ON cash_drivers(user_id, created_at DESC);

-- cash_recommendations: actionable advice tied to a snapshot
CREATE TABLE IF NOT EXISTS cash_recommendations (
    id              VARCHAR(36)    PRIMARY KEY,
    snapshot_id     VARCHAR(36)    NOT NULL REFERENCES cash_risk_snapshots(id) ON DELETE CASCADE,
    user_id         VARCHAR(36)    NOT NULL,
    action_type     VARCHAR(40)    NOT NULL, -- DELAY_SUPPLIER|SEND_REMINDERS|REDUCE_SPEND|REQUEST_CREDIT|ACCELERATE_RECEIVABLES
    description     TEXT           NOT NULL,
    estimated_impact NUMERIC(18,2),          -- euros gained / saved
    horizon_days    INTEGER,                  -- days until impact is felt
    confidence      NUMERIC(4,3),             -- 0.0 – 1.0
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING', -- PENDING|APPLIED|DISMISSED
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reco_snapshot   ON cash_recommendations(snapshot_id);
CREATE INDEX idx_reco_user       ON cash_recommendations(user_id, created_at DESC);

-- weekly_briefs: generated narrative summaries for non-finance users
CREATE TABLE IF NOT EXISTS weekly_briefs (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    snapshot_id     VARCHAR(36)    REFERENCES cash_risk_snapshots(id),
    brief_text      TEXT           NOT NULL,
    risk_level      VARCHAR(10)    NOT NULL,
    runway_days     INTEGER,
    generated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    generation_mode VARCHAR(10)    NOT NULL DEFAULT 'CRON' -- CRON|ON_DEMAND
);
CREATE INDEX idx_brief_user ON weekly_briefs(user_id, generated_at DESC);

-- decision_audit_log: immutable record of every generated recommendation
CREATE TABLE IF NOT EXISTS decision_audit_log (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(36)    NOT NULL,
    event_type      VARCHAR(40)    NOT NULL, -- SNAPSHOT_CREATED|RECOMMENDATION_APPLIED|RECOMMENDATION_DISMISSED|BRIEF_GENERATED
    entity_id       VARCHAR(36),
    entity_type     VARCHAR(30),
    payload         JSONB,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user ON decision_audit_log(user_id, created_at DESC);