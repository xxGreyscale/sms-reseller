-- V4__create_outbox.sql (wallet-service)
-- Transactional outbox table for wallet-service events (LowCreditAlert, ExpiryWarning, etc.)
-- Published to wallet.events TopicExchange by OutboxRelay.
-- Copied from identity-service V3__create_outbox.sql — package/exchange only differ.

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
