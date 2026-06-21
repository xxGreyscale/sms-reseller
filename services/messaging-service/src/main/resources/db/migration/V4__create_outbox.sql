-- V4: transactional outbox table
-- Mirrors identity-service and payment-service outbox tables exactly (Pattern 4).
-- OutboxRelay publishes unsent rows to messaging.events topic exchange at-least-once.

CREATE TABLE outbox (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID    NOT NULL UNIQUE,           -- consumer deduplication key
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT    NOT NULL,                  -- JSON-serialized event payload
    sent           BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

-- Partial index: OutboxRelay only scans unsent rows (avoids full table scan as table grows)
CREATE INDEX idx_outbox_unsent ON outbox (created_at) WHERE sent = false;
