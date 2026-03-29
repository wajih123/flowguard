-- V27: Add composite index on alerts(user_id, type, created_at) to support
-- the deduplication check in SpendingPatternService (existsByUserTypeAndCreatedToday).
-- Also documents the two new alert types introduced in this version:
--   EXCESSIVE_SPEND  — daily or weekend spending spike above user threshold
--   HIDDEN_SUBSCRIPTION — detected recurring charge not tagged as ABONNEMENT

CREATE INDEX IF NOT EXISTS idx_alerts_user_type_created
    ON alerts (user_id, type, created_at);
