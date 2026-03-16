-- ==============================================
-- FlowGuard — V9 Email OTP 2FA
-- ==============================================
-- OTP sessions are stored entirely in Redis (TTL = 10 min).
-- This migration adds per-user MFA toggle for future admin control.
-- By default every account requires email OTP on login.

ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT TRUE;
