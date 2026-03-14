-- ==============================================
-- FlowGuard — V6 Bridge API schema
-- Adds bridge_user_uuid to users.
-- Adds account_name, account_type, sync_status, last_sync_at to accounts.
-- ==============================================

-- Users: store Bridge API user UUID
ALTER TABLE users ADD COLUMN IF NOT EXISTS bridge_user_uuid VARCHAR(255);

-- Accounts: fields needed by Bridge aggregation
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_name  VARCHAR(255);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_type  VARCHAR(100);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS sync_status   VARCHAR(20) NOT NULL DEFAULT 'OK';
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_sync_at  TIMESTAMPTZ;
