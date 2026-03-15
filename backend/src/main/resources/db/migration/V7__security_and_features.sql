-- ==============================================
-- FlowGuard — V7 Security, Features & Compliance
-- ==============================================

-- ── Refresh Tokens (JWT rotation) ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the opaque token
    device_info  VARCHAR(255),
    ip_address   VARCHAR(45),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT false,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash  ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at) WHERE NOT revoked;

-- ── Push Notification Tokens ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS push_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token   TEXT        NOT NULL,
    device_type VARCHAR(20) NOT NULL DEFAULT 'MOBILE', -- MOBILE, WEB
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_push_tokens_unique ON push_tokens(user_id, fcm_token);
CREATE INDEX idx_push_tokens_active ON push_tokens(user_id) WHERE active = true;

-- ── DSP2 Consent Records ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS consent_records (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consent_type    VARCHAR(50) NOT NULL, -- OPEN_BANKING, DATA_PROCESSING, MARKETING
    provider        VARCHAR(50),          -- BRIDGE, ONFIDO, etc.
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,          -- 90 days for Bridge DSP2
    revoked_at      TIMESTAMPTZ,
    ip_address      VARCHAR(45),
    renewed_from_id UUID        REFERENCES consent_records(id)
);
CREATE INDEX idx_consent_user ON consent_records(user_id, consent_type);

-- ── Recurring Transaction Patterns ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recurring_patterns (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    normalized_label VARCHAR(255) NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    avg_amount       NUMERIC(15,2) NOT NULL,
    amount_std       NUMERIC(15,2) NOT NULL DEFAULT 0,
    periodicity      VARCHAR(20)  NOT NULL, -- MONTHLY, QUARTERLY, ANNUAL, WEEKLY
    last_seen        DATE         NOT NULL,
    next_expected    DATE,
    occurrence_count INT          NOT NULL DEFAULT 1,
    confidence       NUMERIC(5,4) NOT NULL DEFAULT 0.5,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (account_id, normalized_label)
);
CREATE INDEX idx_recurring_account ON recurring_patterns(account_id);
CREATE INDEX idx_recurring_next    ON recurring_patterns(next_expected) WHERE next_expected IS NOT NULL;

-- ── API Keys (B2B Scale) ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS api_keys (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 of the raw API key
    key_prefix  VARCHAR(8)   NOT NULL,          -- first 8 chars for display
    scopes      VARCHAR(500) NOT NULL DEFAULT 'read',
    last_used   TIMESTAMPTZ,
    revoked     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_user   ON api_keys(user_id);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_hash);

-- ── IP Blocklist ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ip_blocklist (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address  VARCHAR(45) NOT NULL UNIQUE,
    reason      VARCHAR(255),
    blocked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    created_by  UUID        REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_ip_blocklist_ip ON ip_blocklist(ip_address);

-- ── Notifications audit ────────────────────────────────────────────────────────
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS push_sent   BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS push_sent_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS metadata   JSONB;

-- ── Index for audit_log performance ───────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action, created_at);
