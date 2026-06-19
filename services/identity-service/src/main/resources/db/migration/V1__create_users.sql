-- V1: Create users table
-- Part of identity schema (services/identity-service owns this Flyway migration set).
-- UTF-8 encoding: PostgreSQL uses UTF-8 by default — Swahili/Tanzanian names stored correctly.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_phone_unique UNIQUE (phone)
);

COMMENT ON TABLE users IS 'User accounts with NIDA-verified identity status. One account per NIDA-registered individual.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt password hash — never expose in API responses.';
COMMENT ON COLUMN users.status IS 'VerificationStatus enum: PENDING_VERIFICATION | VERIFIED';
