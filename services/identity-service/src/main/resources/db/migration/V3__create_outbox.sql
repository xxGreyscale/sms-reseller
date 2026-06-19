-- V3: Transactional outbox for async domain events (IDEN-03)
-- The relay job polls unsent rows and publishes to RabbitMQ (at-least-once).
-- Consumers deduplicate by event_id (Pitfall 5 from 02-RESEARCH).

CREATE TABLE IF NOT EXISTS outbox (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    event_id       UUID        NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        TEXT        NOT NULL,
    sent           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_sent ON outbox (sent) WHERE sent = FALSE;
