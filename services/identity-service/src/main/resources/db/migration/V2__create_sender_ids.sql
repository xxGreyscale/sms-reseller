-- V2: Platform-internal numeric sender IDs (SNDR-01)
-- One row per user; sender_id is a 6-digit zero-padded numeric shortcode
-- Placeholder until TCRA provisioning assigns real alphanumeric sender IDs (Phase 4, SNDR-02).

CREATE TABLE IF NOT EXISTS sender_ids (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES users(id),
    sender_id  VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_sender_ids_sender_id UNIQUE (sender_id)
);
