-- ==============================================
-- FlowGuard — V5 Admin tables
-- Adds: feature_flags, system_config, audit_log, application_logs
-- ==============================================

-- ── Feature Flags ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS feature_flags (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key    VARCHAR(100) NOT NULL UNIQUE,
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    description TEXT,
    updated_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO feature_flags (flag_key, enabled, description) VALUES
    ('flash_credit_enabled',     true,  'Active le module Flash Crédit'),
    ('ml_forecast_enabled',      true,  'Active les prévisions ML'),
    ('nordigen_sync_enabled',     true,  'Active la synchro bancaire Nordigen'),
    ('swan_payments_enabled',     false, 'Active les paiements Swan (bêta)'),
    ('beta_analytics_enabled',    false, 'Active les analytics avancées (bêta)'),
    ('maintenance_mode',          false, 'Met l''application en mode maintenance')
ON CONFLICT (flag_key) DO NOTHING;

-- ── System Config ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_config (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT         NOT NULL,
    value_type  VARCHAR(20)  NOT NULL DEFAULT 'STRING',  -- STRING, INTEGER, DECIMAL, BOOLEAN
    description TEXT,
    updated_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO system_config (config_key, config_value, value_type, description) VALUES
    ('max_credit_amount',         '50000',   'INTEGER', 'Montant maximum d''un Flash Crédit en €'),
    ('min_credit_amount',         '500',     'INTEGER', 'Montant minimum d''un Flash Crédit en €'),
    ('default_credit_fee_pct',    '1.50',    'DECIMAL', 'Commission par défaut sur crédits (%)'),
    ('max_credit_duration_days',  '90',      'INTEGER', 'Durée maximum d''un crédit en jours'),
    ('ml_retrain_schedule_hours', '24',      'INTEGER', 'Intervalle de ré-entraînement ML (heures)'),
    ('alert_critical_threshold',  '10000',   'INTEGER', 'Seuil de montant pour alertes critiques (€)'),
    ('support_email',             'support@flowguard.io', 'STRING', 'Email de support affiché dans l''app'),
    ('max_accounts_per_user',     '5',       'INTEGER', 'Nombre maximum de comptes bancaires par utilisateur')
ON CONFLICT (config_key) DO NOTHING;

-- ── Audit Log ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
    actor_email  VARCHAR(255),
    actor_role   VARCHAR(50),
    action       VARCHAR(200) NOT NULL,
    target_type  VARCHAR(100),
    target_id    VARCHAR(255),
    details      JSONB,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor_id    ON audit_log(actor_id);
CREATE INDEX idx_audit_log_created_at  ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_action      ON audit_log(action);
CREATE INDEX idx_audit_log_target      ON audit_log(target_type, target_id);

-- ── Application Logs ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS application_logs (
    id         BIGSERIAL    PRIMARY KEY,
    level      VARCHAR(10)  NOT NULL,   -- DEBUG, INFO, WARN, ERROR
    logger     VARCHAR(255),
    message    TEXT         NOT NULL,
    stack_trace TEXT,
    context    JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_logs_level      ON application_logs(level);
CREATE INDEX idx_app_logs_created_at ON application_logs(created_at DESC);

-- ── Users table: add role column (if not already present from V1) ─────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS disabled_reason VARCHAR(500);
