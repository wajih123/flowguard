-- V8: Upgrade alerts with decision-engine context
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS suggested_action TEXT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(36) REFERENCES cash_risk_snapshots(id);
-- Extend severity to include CRITICAL
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_severity_check;
ALTER TABLE alerts ADD CONSTRAINT alerts_severity_check
    CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO'));
-- alert_thresholds: user-configurable trigger thresholds
CREATE TABLE IF NOT EXISTS alert_thresholds (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alert_type      VARCHAR(50) NOT NULL,
    min_amount      NUMERIC(18,2),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    min_severity    VARCHAR(10) NOT NULL DEFAULT 'LOW',
    UNIQUE(user_id, alert_type)
);