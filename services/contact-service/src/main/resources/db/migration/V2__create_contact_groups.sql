-- V2__create_contact_groups.sql
-- Named contact groups with membership join table (CONT-04)
-- Phase 4, Plan 02

CREATE TABLE contact_groups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_contact_groups_user_id ON contact_groups (user_id);

CREATE TABLE contact_group_members (
    group_id   UUID NOT NULL REFERENCES contact_groups(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, contact_id)
);
