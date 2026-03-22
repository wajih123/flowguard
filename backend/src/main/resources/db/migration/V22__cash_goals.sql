-- ==============================================
-- FlowGuard — V22 Cash Reserve Goals
-- ==============================================

CREATE TABLE IF NOT EXISTS cash_goals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_amount NUMERIC(12, 2) NOT NULL,
    label       VARCHAR(200) NOT NULL DEFAULT 'Réserve de trésorerie',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cash_goals_user_id ON cash_goals (user_id);

COMMENT ON TABLE cash_goals IS 'User-defined cash reserve savings goals';
