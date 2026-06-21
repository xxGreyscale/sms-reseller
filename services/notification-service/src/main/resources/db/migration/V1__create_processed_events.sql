-- processed_events: idempotency guard for inbound AMQP consumer messages (T-05-14)
-- PRIMARY KEY on event_id — ON CONFLICT DO NOTHING prevents duplicate notification creation.
CREATE TABLE processed_events (
    event_id     VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

COMMENT ON TABLE processed_events IS
    'Records event IDs of successfully processed inbound AMQP events. '
    'INSERT ... ON CONFLICT DO NOTHING is the idempotency guard — if the row already exists, '
    'the consumer returns early without creating a duplicate notification (T-05-14).';
