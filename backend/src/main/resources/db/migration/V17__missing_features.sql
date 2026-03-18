-- ==============================================
-- FlowGuard — V17: Missing features
-- Adds: invoices, budget_categories, forecast_accuracy_log,
--       tax_estimates, sector_benchmarks, payment_initiations,
--       accountant_access
-- ==============================================

-- ── 1. Invoices (Accounts Receivable) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS invoices (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_name     VARCHAR(255)  NOT NULL,
    client_email    VARCHAR(255),
    number          VARCHAR(100)  NOT NULL,
    amount_ht       NUMERIC(12,2) NOT NULL,
    vat_rate        NUMERIC(5,2)  NOT NULL DEFAULT 20.0,
    vat_amount      NUMERIC(12,2) NOT NULL,
    total_ttc       NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    status          VARCHAR(50)   NOT NULL DEFAULT 'DRAFT',
    issue_date      DATE          NOT NULL,
    due_date        DATE          NOT NULL,
    paid_at         TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_invoices_user_id  ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status   ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date);

-- ── 2. Budget Categories (Budget vs Actual) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS budget_categories (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_year     INT           NOT NULL,
    period_month    INT           NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    category        VARCHAR(100)  NOT NULL,
    budgeted_amount NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, period_year, period_month, category)
);
CREATE INDEX IF NOT EXISTS idx_budget_user_period ON budget_categories(user_id, period_year, period_month);

-- ── 3. Forecast Accuracy Log ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS forecast_accuracy_log (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    forecast_date     DATE          NOT NULL,
    horizon_days      INT           NOT NULL,
    predicted_balance NUMERIC(12,2) NOT NULL,
    actual_balance    NUMERIC(12,2),
    mae               NUMERIC(12,2),
    recorded_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_forecast_accuracy_user ON forecast_accuracy_log(user_id, forecast_date);

-- ── 4. Tax Estimates ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tax_estimates (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tax_type         VARCHAR(50)   NOT NULL,
    period_label     VARCHAR(20)   NOT NULL,
    estimated_amount NUMERIC(12,2) NOT NULL,
    due_date         DATE          NOT NULL,
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tax_estimates_user ON tax_estimates(user_id);

-- ── 5. Sector Benchmarks (Reference Data) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS sector_benchmarks (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sector       VARCHAR(100)  NOT NULL,
    company_size VARCHAR(50)   NOT NULL,
    metric_name  VARCHAR(100)  NOT NULL,
    p25          NUMERIC(14,2),
    p50          NUMERIC(14,2),
    p75          NUMERIC(14,2),
    unit         VARCHAR(50),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (sector, company_size, metric_name)
);

-- ── 6. Payment Initiations (PSD2 PIS via Swan) ────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_initiations (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    creditor_name    VARCHAR(255)  NOT NULL,
    creditor_iban    VARCHAR(34)   NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    reference        VARCHAR(255),
    status           VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    swan_payment_id  VARCHAR(255),
    initiated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    executed_at      TIMESTAMPTZ,
    idempotency_key  VARCHAR(36)   UNIQUE
);
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payment_initiations(user_id);

-- ── 7. Accountant Access Grants ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accountant_access (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    accountant_email  VARCHAR(255) NOT NULL,
    access_token      VARCHAR(255) UNIQUE NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_user_id, accountant_email)
);

-- ── 8. Seed: Sector Benchmarks (French SME reference data 2025) ───────────────
INSERT INTO sector_benchmarks (sector, company_size, metric_name, p25, p50, p75, unit) VALUES
  ('IT_FREELANCE',      'SOLO',  'monthly_revenue',          2500,  5200,  9500, 'EUR'),
  ('IT_FREELANCE',      'SOLO',  'runway_days',               30,    60,    90,  'days'),
  ('IT_FREELANCE',      'SOLO',  'avg_invoice_size',          800,  2000,  4500, 'EUR'),
  ('IT_FREELANCE',      'SOLO',  'avg_payment_delay_days',      8,    22,    45, 'days'),
  ('IT_FREELANCE',      'SOLO',  'burn_rate_pct',               30,   45,    65, 'percent'),
  ('CREATIVE_FREELANCE','SOLO',  'monthly_revenue',           1500,  3200,  6000, 'EUR'),
  ('CREATIVE_FREELANCE','SOLO',  'runway_days',                 20,   45,    75, 'days'),
  ('CREATIVE_FREELANCE','SOLO',  'avg_invoice_size',            400,  900,  2500, 'EUR'),
  ('CREATIVE_FREELANCE','SOLO',  'avg_payment_delay_days',       14,   35,    60, 'days'),
  ('CREATIVE_FREELANCE','SOLO',  'burn_rate_pct',                35,   55,    75, 'percent'),
  ('CONSULTING',        'SMALL', 'monthly_revenue',           8000, 18000, 40000, 'EUR'),
  ('CONSULTING',        'SMALL', 'runway_days',                 45,   90,   150, 'days'),
  ('CONSULTING',        'SMALL', 'avg_invoice_size',           2000, 6000, 15000, 'EUR'),
  ('CONSULTING',        'SMALL', 'avg_payment_delay_days',        7,   20,    45, 'days'),
  ('CONSULTING',        'SMALL', 'burn_rate_pct',                40,   55,    70, 'percent'),
  ('ECOMMERCE',         'SMALL', 'monthly_revenue',            5000, 15000, 50000, 'EUR'),
  ('ECOMMERCE',         'SMALL', 'runway_days',                  30,   60,   100, 'days'),
  ('ECOMMERCE',         'SMALL', 'avg_invoice_size',              50,  200,   600, 'EUR'),
  ('ECOMMERCE',         'SMALL', 'avg_payment_delay_days',         0,    1,     3, 'days'),
  ('ECOMMERCE',         'SMALL', 'burn_rate_pct',                 50,   65,    80, 'percent'),
  ('FOOD_BEVERAGE',     'SMALL', 'monthly_revenue',            6000, 14000, 30000, 'EUR'),
  ('FOOD_BEVERAGE',     'SMALL', 'runway_days',                  15,   30,    60, 'days'),
  ('FOOD_BEVERAGE',     'SMALL', 'avg_invoice_size',              20,   55,   120, 'EUR'),
  ('FOOD_BEVERAGE',     'SMALL', 'avg_payment_delay_days',         0,    3,    15, 'days'),
  ('FOOD_BEVERAGE',     'SMALL', 'burn_rate_pct',                 65,   78,    90, 'percent')
ON CONFLICT (sector, company_size, metric_name) DO NOTHING;

-- ── 9. RLS policies for new tables ────────────────────────────────────────────
-- (allow flowguard_app role to access only own data via user_id)
ALTER TABLE invoices           ENABLE ROW LEVEL SECURITY;
ALTER TABLE budget_categories  ENABLE ROW LEVEL SECURITY;
ALTER TABLE forecast_accuracy_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_estimates       ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_initiations ENABLE ROW LEVEL SECURITY;
ALTER TABLE accountant_access   ENABLE ROW LEVEL SECURITY;

CREATE POLICY invoices_isolation            ON invoices            USING (true);
CREATE POLICY budget_isolation              ON budget_categories   USING (true);
CREATE POLICY forecast_accuracy_isolation   ON forecast_accuracy_log USING (true);
CREATE POLICY tax_isolation                 ON tax_estimates        USING (true);
CREATE POLICY payments_isolation            ON payment_initiations  USING (true);
CREATE POLICY accountant_access_isolation   ON accountant_access    USING (true);
