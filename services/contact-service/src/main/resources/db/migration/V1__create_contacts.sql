-- V1__create_contacts.sql
-- Contact-service contacts table (CONT-01/02/03/06)
-- Phase 4, Plan 02

CREATE TABLE contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    name        VARCHAR(255),
    phone_e164  VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_contact_user_phone UNIQUE (user_id, phone_e164)
);

CREATE INDEX idx_contacts_user_id ON contacts (user_id);
