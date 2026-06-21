-- V1__create_processed_events.sql
-- Idempotency guard table for admin-service domain event consumers.
-- Pattern: INSERT ON CONFLICT DO NOTHING (ProcessedEventRepository.tryInsert).
-- Same pattern as wallet-service V1 and notification-service V1.

CREATE TABLE IF NOT EXISTS admin.processed_events (
    event_id        VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT processed_events_pkey PRIMARY KEY (event_id)
);
