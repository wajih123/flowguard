-- ==============================================
-- FlowGuard — V4 Seed test users
-- Passwords: "Test1234!" — bcrypt cost 10
-- Hash: $2b$10$3Q1Rt6Ha5XOonNiyMVodouex5JD9u9hQvIIqNdQjNAxNlWQA2L/ye
-- ==============================================

-- alice: ROLE_USER, KYC APPROVED
INSERT INTO users (id, first_name, last_name, email, password_hash, company_name, user_type, kyc_status, role, disabled)
VALUES (
    gen_random_uuid(),
    'Alice', 'Martin',
    'alice@dev.fr',
    '$2b$10$3Q1Rt6Ha5XOonNiyMVodouex5JD9u9hQvIIqNdQjNAxNlWQA2L/ye',
    'Alice Corp',
    'TPE',
    'APPROVED',
    'ROLE_USER',
    false
)
ON CONFLICT (email) DO NOTHING;

-- bob: ROLE_USER, KYC APPROVED
INSERT INTO users (id, first_name, last_name, email, password_hash, company_name, user_type, kyc_status, role, disabled)
VALUES (
    gen_random_uuid(),
    'Bob', 'Dupont',
    'bob@dev.fr',
    '$2b$10$3Q1Rt6Ha5XOonNiyMVodouex5JD9u9hQvIIqNdQjNAxNlWQA2L/ye',
    'Bob SAS',
    'PME',
    'APPROVED',
    'ROLE_USER',
    false
)
ON CONFLICT (email) DO NOTHING;

-- admin: ROLE_ADMIN
INSERT INTO users (id, first_name, last_name, email, password_hash, company_name, user_type, kyc_status, role, disabled)
VALUES (
    gen_random_uuid(),
    'Admin', 'FlowGuard',
    'admin@dev.fr',
    '$2b$10$3Q1Rt6Ha5XOonNiyMVodouex5JD9u9hQvIIqNdQjNAxNlWQA2L/ye',
    'FlowGuard',
    'TPE',
    'APPROVED',
    'ROLE_ADMIN',
    false
)
ON CONFLICT (email) DO NOTHING;
