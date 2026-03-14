-- ==============================================
-- FlowGuard — V1 Initial Schema
-- ==============================================

-- Users
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name    VARCHAR(255)  NOT NULL,
    last_name     VARCHAR(255)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    company_name  VARCHAR(255)  NOT NULL,
    user_type     VARCHAR(50)   NOT NULL,
    kyc_status    VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    swan_onboarding_id    VARCHAR(255),
    swan_account_id       VARCHAR(255),
    nordigen_requisition_id VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);

-- Accounts
CREATE TABLE accounts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    iban                 VARCHAR(34)    NOT NULL UNIQUE,
    bic                  VARCHAR(11)    NOT NULL,
    balance              NUMERIC(15,2)  NOT NULL DEFAULT 0,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    bank_name            VARCHAR(255),
    external_account_id  VARCHAR(255),
    status               VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    last_sync_date       DATE,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- Transactions
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id              UUID           NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    amount                  NUMERIC(15,2)  NOT NULL,
    type                    VARCHAR(10)    NOT NULL,
    label                   VARCHAR(500)   NOT NULL,
    category                VARCHAR(50)    NOT NULL,
    date                    DATE           NOT NULL,
    is_recurring            BOOLEAN        NOT NULL DEFAULT false,
    external_transaction_id VARCHAR(255),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_date ON transactions(account_id, date);
CREATE INDEX idx_transactions_category ON transactions(account_id, category);

-- Alerts
CREATE TABLE alerts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type              VARCHAR(50)    NOT NULL,
    severity          VARCHAR(20)    NOT NULL,
    message           VARCHAR(1000)  NOT NULL,
    projected_deficit NUMERIC(15,2),
    trigger_date      DATE,
    is_read           BOOLEAN        NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_user_id ON alerts(user_id);
CREATE INDEX idx_alerts_unread ON alerts(user_id, is_read) WHERE is_read = false;

-- Flash Credits
CREATE TABLE flash_credits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount          NUMERIC(10,2)  NOT NULL,
    fee             NUMERIC(10,2)  NOT NULL,
    total_repayment NUMERIC(10,2)  NOT NULL,
    purpose         VARCHAR(500)   NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    disbursed_at    TIMESTAMPTZ,
    repaid_at       TIMESTAMPTZ,
    due_date        TIMESTAMPTZ    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_flash_credits_user_id ON flash_credits(user_id);
CREATE INDEX idx_flash_credits_active ON flash_credits(user_id, status)
    WHERE status IN ('APPROVED', 'DISBURSED');
