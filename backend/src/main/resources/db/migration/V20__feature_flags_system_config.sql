-- ==============================================
-- FlowGuard — V20 Feature Flags & System Config
-- ==============================================

-- ── Feature Flags ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS feature_flags (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key     VARCHAR(50) NOT NULL UNIQUE,
    enabled      BOOLEAN     NOT NULL DEFAULT true,
    description  TEXT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── System Configuration ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_config (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT         NOT NULL,
    value_type   VARCHAR(20)  NOT NULL DEFAULT 'STRING', -- STRING, BOOLEAN, INTEGER, DECIMAL
    description  TEXT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Default Feature Flags ────────────────────────────────────────────────────
INSERT INTO feature_flags (flag_key, enabled, description) VALUES
    ('TRADING_ACCOUNTS', true, 'Enable trading account module'),
    ('BUDGET_FORECASTING', true, 'Enable budget forecasting feature'),
    ('TAX_ESTIMATION', true, 'Enable tax estimation calculations'),
    ('ACCOUNTANT_PORTAL', true, 'Enable accountant access portal'),
    ('FLASH_CREDIT', true, 'Enable flash credit quick loans'),
    ('SANCTIONS_CHECK', true, 'Enable sanctions screening on registration'),
    ('TRACFIN_REPORTING', true, 'Enable TRACFIN AML reporting'),
    ('MFA_ENFORCED', false, 'Enforce MFA for all users'),
    ('ADVANCED_ANALYTICS', true, 'Enable advanced analytics dashboard'),
    ('API_RATE_LIMITING', true, 'Enable API rate limiting')
ON CONFLICT (flag_key) DO NOTHING;

-- ── Default System Configuration ─────────────────────────────────────────────
INSERT INTO system_config (config_key, config_value, value_type, description) VALUES
    ('SUPPORT_EMAIL', 'support@flowguard.fr', 'STRING', 'Support team email address'),
    ('DPO_EMAIL', 'dpo@flowguard.fr', 'STRING', 'Data Protection Officer email'),
    ('COMPLIANCE_OFFICER_EMAIL', 'compliance@flowguard.fr', 'STRING', 'Compliance officer email'),
    ('MAX_LOGIN_ATTEMPTS', '5', 'INTEGER', 'Maximum failed login attempts before lockout'),
    ('LOGIN_LOCKOUT_MINUTES', '15', 'INTEGER', 'Lockout duration in minutes'),
    ('API_RATE_LIMIT_PER_HOUR', '1000', 'INTEGER', 'API calls per hour per user'),
    ('MIN_PASSWORD_LENGTH', '12', 'INTEGER', 'Minimum password length'),
    ('SESSION_TIMEOUT_MINUTES', '30', 'INTEGER', 'Session timeout in minutes'),
    ('TAX_YEAR', '2025', 'INTEGER', 'Current tax year for estimations'),
    ('CURRENCY_DEFAULT', 'EUR', 'STRING', 'Default currency'),
    ('MAINTENANCE_MODE', 'false', 'BOOLEAN', 'Enable maintenance mode'),
    ('ALERT_EMAIL_ENABLED', 'true', 'BOOLEAN', 'Enable email alerts for users')
ON CONFLICT (config_key) DO NOTHING;
