-- V3__create_suppressed_numbers.sql
-- Per-user suppression list (CONT-08, D-08)
-- Phase 4, Plan 02

CREATE TABLE suppressed_numbers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    phone_e164  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppressed_user_phone UNIQUE (user_id, phone_e164)
);

CREATE INDEX idx_suppressed_numbers_user_id ON suppressed_numbers (user_id);
