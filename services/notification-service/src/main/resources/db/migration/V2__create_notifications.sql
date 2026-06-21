-- notifications: in-app notification log (NOTF-01..06)
-- One row per upstream event. Scoped to user_id.
CREATE TABLE notifications (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    type       VARCHAR(64) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT        NOT NULL,
    payload    JSONB,
    read       BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

-- T-05-16: feed queries filter by user_id; created_at DESC ordering is the standard feed sort
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

COMMENT ON TABLE notifications IS
    'In-app notification log. Each row maps 1:1 to a consumed upstream event. '
    'Feed API (GET /api/v1/notifications) returns rows for the authenticated user only '
    'to prevent IDOR (T-05-16).';

COMMENT ON COLUMN notifications.payload IS
    'Event-specific JSONB detail (e.g. amountTzs for PAYMENT_CONFIRMED, availableCredits for LOW_CREDIT). '
    'Null when no extra context is needed.';
