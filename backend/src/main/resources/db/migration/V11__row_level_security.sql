-- ==============================================
-- FlowGuard — V11 PostgreSQL Row-Level Security
-- GDPR Art. 25 (Data Protection by Design), Art. 32 (Security of processing)
-- ==============================================
--
-- Architecture:
--   - Application connects as role 'flowguard_app' (set via DB_APP_ROLE env var)
--   - Service/admin operations use 'flowguard_admin' (bypasses RLS)
--   - Current user context is set via SET LOCAL app.current_user_id = '<uuid>'
--     before each query (injected by RequestContextFilter)
--
-- NOTE: This migration is additive and non-breaking. If 'flowguard_app' or
-- 'flowguard_admin' roles don't exist yet (dev environment), RLS is still
-- enabled but will only enforce for those roles when they exist.
-- ==============================================

-- ── 1. Create application database roles (idempotent) ─────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'flowguard_app') THEN
        CREATE ROLE flowguard_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'flowguard_admin') THEN
        CREATE ROLE flowguard_admin NOLOGIN;
        -- admin role bypasses RLS (BYPASSRLS)
        ALTER ROLE flowguard_admin BYPASSRLS;
    END IF;
END
$$;

-- ── 2. Enable RLS on tables containing personal data ─────────────────────────

-- users table: users can only read/update their own row
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

-- accounts: users can only access their own accounts
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts FORCE ROW LEVEL SECURITY;

-- transactions: accessible only via account owner chain
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;

-- alerts: per-user alerts
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts FORCE ROW LEVEL SECURITY;

-- flash_credits: per-user credits
ALTER TABLE flash_credits ENABLE ROW LEVEL SECURITY;
ALTER TABLE flash_credits FORCE ROW LEVEL SECURITY;

-- refresh_tokens: per-user tokens (V7)
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;

-- push_tokens: per-user push tokens (V7)
ALTER TABLE push_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_tokens FORCE ROW LEVEL SECURITY;

-- consent_records: per-user consent (V7)
ALTER TABLE consent_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent_records FORCE ROW LEVEL SECURITY;

-- ── 3. RLS Policies — users table ─────────────────────────────────────────────

-- Users can SELECT only their own row
CREATE POLICY users_select_own
    ON users
    FOR SELECT
    TO flowguard_app
    USING (id = current_setting('app.current_user_id', true)::uuid);

-- Users can UPDATE only their own row
CREATE POLICY users_update_own
    ON users
    FOR UPDATE
    TO flowguard_app
    USING (id = current_setting('app.current_user_id', true)::uuid);

-- INSERT allowed (registration — no user context yet)
CREATE POLICY users_insert
    ON users
    FOR INSERT
    TO flowguard_app
    WITH CHECK (true);

-- ── 4. RLS Policies — accounts table ──────────────────────────────────────────

CREATE POLICY accounts_select_own
    ON accounts
    FOR SELECT
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY accounts_insert_own
    ON accounts
    FOR INSERT
    TO flowguard_app
    WITH CHECK (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY accounts_update_own
    ON accounts
    FOR UPDATE
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 5. RLS Policies — transactions table ──────────────────────────────────────

-- Transactions are linked to accounts — enforce via subquery
CREATE POLICY transactions_select_own
    ON transactions
    FOR SELECT
    TO flowguard_app
    USING (account_id IN (
        SELECT id FROM accounts
        WHERE user_id = current_setting('app.current_user_id', true)::uuid
    ));

CREATE POLICY transactions_insert_own
    ON transactions
    FOR INSERT
    TO flowguard_app
    WITH CHECK (account_id IN (
        SELECT id FROM accounts
        WHERE user_id = current_setting('app.current_user_id', true)::uuid
    ));

-- ── 6. RLS Policies — alerts table ────────────────────────────────────────────

CREATE POLICY alerts_select_own
    ON alerts
    FOR SELECT
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY alerts_update_own
    ON alerts
    FOR UPDATE
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY alerts_insert_own
    ON alerts
    FOR INSERT
    TO flowguard_app
    WITH CHECK (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 7. RLS Policies — flash_credits table ─────────────────────────────────────

CREATE POLICY flash_credits_select_own
    ON flash_credits
    FOR SELECT
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY flash_credits_insert_own
    ON flash_credits
    FOR INSERT
    TO flowguard_app
    WITH CHECK (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY flash_credits_update_own
    ON flash_credits
    FOR UPDATE
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 8. RLS Policies — refresh_tokens ──────────────────────────────────────────

CREATE POLICY refresh_tokens_own
    ON refresh_tokens
    FOR ALL
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 9. RLS Policies — push_tokens ─────────────────────────────────────────────

CREATE POLICY push_tokens_own
    ON push_tokens
    FOR ALL
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 10. RLS Policies — consent_records ────────────────────────────────────────

CREATE POLICY consent_records_own
    ON consent_records
    FOR ALL
    TO flowguard_app
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- ── 11. Grant minimum privileges to flowguard_app ─────────────────────────────

-- Grant the application role access to tables (owner keeps full access)
GRANT SELECT, INSERT, UPDATE ON users TO flowguard_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON accounts TO flowguard_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON transactions TO flowguard_app;
GRANT SELECT, INSERT, UPDATE ON alerts TO flowguard_app;
GRANT SELECT, INSERT, UPDATE ON flash_credits TO flowguard_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO flowguard_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON push_tokens TO flowguard_app;
GRANT SELECT, INSERT, UPDATE ON consent_records TO flowguard_app;

-- Read-only access to compliance tables for flowguard_app (admins use flowguard_admin)
GRANT SELECT, INSERT ON sanctions_screening_log TO flowguard_app;
GRANT SELECT, INSERT ON tracfin_reports TO flowguard_app;
GRANT UPDATE ON tracfin_reports TO flowguard_admin;

COMMENT ON SCHEMA public IS
    'Row-Level Security enabled (GDPR Art. 25 + Art. 32). '
    'App role: flowguard_app (enforces RLS). Admin role: flowguard_admin (bypasses RLS).';
