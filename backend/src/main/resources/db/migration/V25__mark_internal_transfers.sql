-- V25: Mark internal transfers for exclusion from income/expense calculations
-- Add is_internal flag to transactions table to identify inter-account transfers.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS is_internal BOOLEAN NOT NULL DEFAULT false;

-- Backfill: mark existing internal transfers
UPDATE transactions SET is_internal = true
WHERE label ILIKE '%vir inst vers%'
   OR label ILIKE '%virement web%'
   OR label ILIKE '%virement de%'
   OR label ILIKE '%vir de%'
   OR label ILIKE '%ret dab%'
   OR label ILIKE '%retrait dab%';

-- Index for efficient filtering
CREATE INDEX IF NOT EXISTS idx_transactions_internal ON transactions(is_internal);

-- Constraint: forbid furhter duplication of account UNIQUE constraints already added in V24
-- This just ensures the scoring service consistently ignores these transfers
