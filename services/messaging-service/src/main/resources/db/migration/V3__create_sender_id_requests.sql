-- V3: sender_id_requests table
-- SNDR-02/03: User submits sender ID request; admin approves or rejects

CREATE TABLE sender_id_requests (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    sender_name   VARCHAR(11)  NOT NULL,         -- alphanumeric sender ID (max 11 chars per GSM spec)
    status        VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    reject_reason TEXT,
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sender_id_requests_user_id ON sender_id_requests (user_id);
CREATE INDEX idx_sender_id_requests_status  ON sender_id_requests (status);
