-- V4__create_outbox.sql
-- Transactional outbox for payment-service (copied from identity-service V3__create_outbox.sql)
-- Used by PaymentConfirmed event relay to payment.events (Plan 05).

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

COMMENT ON TABLE outbox IS 'Transactional outbox for payment-service — ensures at-least-once delivery of PaymentConfirmed events to payment.events exchange.';
COMMENT ON COLUMN outbox.event_id IS 'Idempotency key — UNIQUE constraint prevents double-emit even on retry (T-03-11).';
