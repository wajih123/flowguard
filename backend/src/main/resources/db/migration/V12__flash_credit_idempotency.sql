-- ==============================================
-- FlowGuard — V12 Flash Credit Idempotency
-- Financial integrity — prevents duplicate credit disbursals on network retry
-- ==============================================

ALTER TABLE flash_credits
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(36) UNIQUE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_flash_credits_idempotency
    ON flash_credits(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN flash_credits.idempotency_key IS
    'Client-supplied UUID idempotency key. If the same key is presented on retry, '
    'the existing credit is returned instead of creating a new one. '
    'Clients should generate a fresh UUID per credit request and reuse on retry only.';
