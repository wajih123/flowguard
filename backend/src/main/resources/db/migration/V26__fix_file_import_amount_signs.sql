-- V26: Fix amount signs for transactions imported from file uploads (CSV, PDF, MT940, CFONB, OFX, XLSX).
--
-- ROOT CAUSE (fixed 2026-03-27):
--   BankStatementParserService always returned ABSOLUTE (positive) amounts regardless of type.
--   TransactionService.importFromStatement stored row.amount() as-is without normalising sign.
--   Result: DEBIT transactions (CARTE, PRLV, RETRAIT…) were stored with a POSITIVE amount,
--   making DashboardResource count them as income instead of expenses.
--
-- WHAT THIS MIGRATION DOES:
--   1. All file-imported DEBIT transactions with a positive amount → negate (positive → negative).
--   2. All file-imported CREDIT transactions with a negative amount → negate (negative → positive).
--      (Edge case: defensive, should not exist in practice but handled for correctness.)
--   Bridge API (import_source = 'BRIDGE_API') transactions are left untouched — they were
--   already stored with the correct sign by BankAccountSyncService.
--   Manually entered transactions (import_source = 'MANUAL') are also left untouched.

-- Step 1: DEBIT transactions from file imports that were incorrectly stored as positive
UPDATE transactions
SET amount = -amount
WHERE type = 'DEBIT'
  AND amount > 0
  AND import_source NOT IN ('BRIDGE_API', 'MANUAL');

-- Step 2 (defensive): CREDIT transactions from file imports stored as negative
UPDATE transactions
SET amount = -amount
WHERE type = 'CREDIT'
  AND amount < 0
  AND import_source NOT IN ('BRIDGE_API', 'MANUAL');
