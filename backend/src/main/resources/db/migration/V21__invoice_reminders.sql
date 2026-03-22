-- ==============================================
-- FlowGuard — V21 Invoice Reminder Settings
-- ==============================================

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS reminder_enabled BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN invoices.reminder_enabled IS 'Whether automatic overdue reminders are enabled for this invoice';
