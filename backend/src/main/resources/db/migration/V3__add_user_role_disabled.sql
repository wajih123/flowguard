-- ==============================================
-- FlowGuard — V3 User security columns
-- Adds: role, disabled, disabled_at, disabled_reason
-- ==============================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS role         VARCHAR(30)  NOT NULL DEFAULT 'ROLE_USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled     BOOLEAN      NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled_at  TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled_reason VARCHAR(500);
