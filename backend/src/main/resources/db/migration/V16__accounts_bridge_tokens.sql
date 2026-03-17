-- ==============================================
-- FlowGuard — V16 Add Bridge token columns to accounts
-- AccountEntity references bridge_access_token, bridge_refresh_token,
-- and bridge_consent_expires_at but these were never created in migrations.
-- Tokens are stored AES-256-GCM encrypted at rest (length 1024).
-- ==============================================

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS bridge_access_token    VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS bridge_refresh_token   VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS bridge_consent_expires_at TIMESTAMPTZ;
