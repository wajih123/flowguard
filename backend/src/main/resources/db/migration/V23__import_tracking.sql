-- V23: Import tracking, running balances, and daily_balances materialised view
-- Implements BA Gaps 2, 3 and 6 identified during the historical-statement import analysis.

-- ─── Gap 3: provenance columns on transactions ──────────────────────────────
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS import_source   VARCHAR(20)    NOT NULL DEFAULT 'BRIDGE_API',
    ADD COLUMN IF NOT EXISTS import_batch_id UUID,
    ADD COLUMN IF NOT EXISTS is_historical   BOOLEAN        NOT NULL DEFAULT FALSE;

-- ─── Gap 2: bank-certified running balance from PDF/OFX ─────────────────────
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS balance_after   NUMERIC(15, 2);

-- ─── Gap 3: import_batches — one row per file upload ────────────────────────
-- Enables per-batch rollback and gives the UI a "past imports" log.
CREATE TABLE IF NOT EXISTS import_batches (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    filename      TEXT         NOT NULL,
    format        VARCHAR(20)  NOT NULL,               -- PDF | CSV | OFX | MT940 | CFONB | XLSX
    imported_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tx_imported   INT          NOT NULL DEFAULT 0,
    tx_skipped    INT          NOT NULL DEFAULT 0,
    date_from     DATE,
    date_to       DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'  -- COMPLETED | FAILED | PARTIAL
);

-- ─── Gap 6: daily_balances materialised view ────────────────────────────────
-- Queried by ml-service/database.py for LSTM training.
-- Uses the bank-certified balance_after where available; otherwise reconstructs
-- a running total from ordered debits/credits.
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_balances AS
SELECT
    t.account_id,
    t.date,
    -- last bank-certified balance on each day (when we have it)
    MAX(t.balance_after) FILTER (WHERE t.balance_after IS NOT NULL) AS certified_balance,
    -- otherwise sum of signed amounts up to and including that day
    SUM(
        CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END
    ) OVER (
        PARTITION BY t.account_id
        ORDER BY t.date
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS reconstructed_balance,
    COUNT(*) AS tx_count
FROM transactions t
GROUP BY t.account_id, t.date, t.type, t.amount, t.balance_after
ORDER BY t.account_id, t.date;

-- Unique index — required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_balances_account_date
    ON daily_balances (account_id, date);

-- ─── Supporting indexes ──────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_transactions_import_batch
    ON transactions (import_batch_id);

CREATE INDEX IF NOT EXISTS idx_transactions_is_historical
    ON transactions (account_id, is_historical);

CREATE INDEX IF NOT EXISTS idx_transactions_balance_after
    ON transactions (account_id, date)
    WHERE balance_after IS NOT NULL;
