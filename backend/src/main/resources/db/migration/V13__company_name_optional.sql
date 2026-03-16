-- V13: Make company_name optional (required for INDIVIDUAL / B2C user types)
ALTER TABLE users ALTER COLUMN company_name DROP NOT NULL;
