-- ==============================================
-- FlowGuard — V8 Fix account uniqueness constraints
--
-- Problem: iban has a global UNIQUE constraint, but Bridge can return multiple
-- accounts sharing the same IBAN (e.g. checking + savings at the same bank,
-- or multiple users connecting to the same sandbox bank account).
-- When the second persist() triggers a Hibernate flush, PostgreSQL aborts the
-- whole transaction and all subsequent DB ops fail with
-- "current transaction is aborted".
--
-- Fix:
--   1. Drop global UNIQUE on iban (IBANs are bank-account-scoped, two Bridge
--      "sub-accounts" can legitimately share one IBAN).
--   2. Add UNIQUE on external_account_id — Bridge account IDs are globally
--      unique; this is the correct natural key.
-- ==============================================

-- Drop the global unique index on iban
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_iban_key;

-- external_account_id is the true natural key coming from Bridge
ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_external_account_id
    UNIQUE (external_account_id);
