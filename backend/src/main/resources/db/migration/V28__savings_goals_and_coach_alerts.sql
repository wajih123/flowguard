-- V28: Multi-goal savings feature + smart coach alert types
--
-- 1. savings_goals  — replaces the single cash_goals row with a full multi-goal table
-- 2. Documents five new AlertType values introduced in this version:
--    SAVINGS_OPPORTUNITY, SUBSCRIPTION_PRICE_INCREASE, FREE_TRIAL_ENDING,
--    DUPLICATE_SUBSCRIPTION, BUDGET_RISK

-- ─── savings_goals ────────────────────────────────────────────────────────────
CREATE TABLE savings_goals (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_type            VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    label                VARCHAR(200) NOT NULL,
    target_amount        NUMERIC(12, 2) NOT NULL,
    target_date          DATE,
    monthly_contribution NUMERIC(12, 2),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_savings_goals_user ON savings_goals (user_id);

-- ─── index to support BUDGET_RISK dedup (same pattern as idx_alerts_user_type_created) ─
-- Already covered by the existing idx_alerts_user_type_created index from V27.
-- No additional DDL required for alert storage.

COMMENT ON TABLE savings_goals IS
    'Multi-goal savings table. Each user may have unlimited goals with independent '
    'target amounts, target dates and optional monthly contribution targets.';

COMMENT ON COLUMN savings_goals.goal_type IS
    'Enum: EMERGENCY_FUND | VACATION | EQUIPMENT | REAL_ESTATE | EDUCATION | RETIREMENT | PROJECT | OTHER';

COMMENT ON COLUMN savings_goals.monthly_contribution IS
    'User-overridden monthly savings amount. NULL = use system recommendation.';
