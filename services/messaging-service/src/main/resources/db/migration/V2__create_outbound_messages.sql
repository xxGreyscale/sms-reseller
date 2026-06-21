-- V2: outbound_messages table
-- MESG-05/07: Per-recipient message rows; lot_id carries credit reservation correlation (D-01/02/04)

CREATE TABLE outbound_messages (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID        NOT NULL REFERENCES campaigns(id),
    user_id     UUID        NOT NULL,
    phone_e164  VARCHAR(20) NOT NULL,
    lot_id      UUID        NOT NULL,          -- credit lot reserved for this message (D-13)
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_id VARCHAR(255),                  -- provider reference (filled on ACCEPT)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbound_messages_campaign ON outbound_messages (campaign_id);
CREATE INDEX idx_outbound_messages_status   ON outbound_messages (campaign_id, status);
