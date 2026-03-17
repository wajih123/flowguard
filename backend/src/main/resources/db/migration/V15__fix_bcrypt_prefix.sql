-- ==============================================
-- FlowGuard — V15 Fix bcrypt prefix $2b$ → $2a$
-- WildFly Elytron BcryptUtil only accepts $2a$/$2y$ format.
-- The V4 seed migration used Python/Node bcrypt $2b$ prefix.
-- This is safe to run on fresh and existing databases (idempotent).
-- ==============================================
UPDATE users
SET    password_hash = REPLACE(password_hash, '$2b$', '$2a$')
WHERE  password_hash LIKE '$2b$%';
