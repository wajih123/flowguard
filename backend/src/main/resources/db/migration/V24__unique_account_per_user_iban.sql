-- V24: Enforce unique (user_id, iban) on accounts to prevent duplicate accounts
-- when the same bank is connected via multiple Bridge items.

-- First, clean up any existing duplicates by keeping the one with the most transactions
-- (this migration should only run in environments that were already cleaned manually,
-- but the DELETE is idempotent if no duplicates exist)
DELETE FROM transactions
WHERE account_id IN (
    SELECT id FROM (
        SELECT a.id,
               ROW_NUMBER() OVER (
                   PARTITION BY a.user_id, a.iban
                   ORDER BY (SELECT COUNT(*) FROM transactions t WHERE t.account_id = a.id) DESC, a.created_at ASC
               ) AS rn
        FROM accounts a
    ) ranked
    WHERE rn > 1
);

DELETE FROM accounts
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, iban
                   ORDER BY created_at ASC
               ) AS rn
        FROM accounts
    ) ranked
    WHERE rn > 1
);

-- Add the unique constraint
ALTER TABLE accounts ADD CONSTRAINT uq_accounts_user_iban UNIQUE (user_id, iban);
