-- ==============================================
-- FlowGuard — V14 Email verified flag
-- ==============================================
-- Tracks whether a user has confirmed their e-mail address.
-- New registrations start as FALSE and must go through /verify-email.
-- Existing users are grandfathered as TRUE (they had no verification gate).
-- Admins added via SQL/back-office are always TRUE via trigger.
-- ==============================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Grandfather all existing accounts — they registered before this feature existed.
UPDATE users SET email_verified = TRUE;

-- ── Auto-verify admin accounts ────────────────────────────────────────────────
-- When a user row is inserted or the role column is updated to an admin role,
-- automatically set email_verified = TRUE so the account is usable immediately
-- without going through the e-mail OTP flow.
-- This covers:
--   • Admins seeded via SQL migration
--   • Admins created via the back-office API
--   • Users promoted to admin role after the fact

CREATE OR REPLACE FUNCTION fn_auto_verify_admin_email()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN') THEN
        NEW.email_verified := TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auto_verify_admin_email ON users;
CREATE TRIGGER trg_auto_verify_admin_email
    BEFORE INSERT OR UPDATE OF role ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_auto_verify_admin_email();
