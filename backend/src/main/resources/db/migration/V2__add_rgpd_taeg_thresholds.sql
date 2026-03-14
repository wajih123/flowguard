-- ==============================================
-- FlowGuard — V2 Schema updates
-- Adds: RGPD fields, TAEG/retraction fields, alert thresholds
-- ==============================================

-- Users — RGPD consent and data deletion fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS gdpr_consent_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS data_deletion_requested_at TIMESTAMPTZ;

-- Flash Credits — TAEG and retraction fields
ALTER TABLE flash_credits ADD COLUMN IF NOT EXISTS taeg_percent NUMERIC(6,2);
ALTER TABLE flash_credits ADD COLUMN IF NOT EXISTS retraction_deadline TIMESTAMPTZ;
ALTER TABLE flash_credits ADD COLUMN IF NOT EXISTS retraction_exercised BOOLEAN NOT NULL DEFAULT false;

-- Alert Thresholds
CREATE TABLE IF NOT EXISTS alert_thresholds (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type    VARCHAR(50)   NOT NULL,
    min_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    enabled       BOOLEAN       NOT NULL DEFAULT true,
    min_severity  VARCHAR(20)   NOT NULL DEFAULT 'LOW',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (user_id, alert_type)
);

CREATE INDEX idx_alert_thresholds_user_id ON alert_thresholds(user_id);
