-- processed_events: idempotency guard for inbound AMQP consumer messages (T-03-08)
-- PRIMARY KEY on event_id — ON CONFLICT DO NOTHING prevents duplicate processing.
CREATE TABLE processed_events (
    event_id    VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

COMMENT ON TABLE processed_events IS
    'Records event IDs of successfully processed inbound AMQP events. '
    'INSERT ... ON CONFLICT DO NOTHING is the idempotency guard — if the row already exists, '
    'the consumer returns early without re-processing (T-03-08).';

COMMENT ON COLUMN processed_events.event_id IS
    'Opaque event identifier from the inbound event payload (e.g. UserVerified.eventId). '
    'Max 128 chars covers UUID (36) and any prefixed variant.';

COMMENT ON COLUMN processed_events.processed_at IS
    'Timestamp when this event was first processed. Useful for operational audits.';
