-- V1: campaigns table
-- MESG-01: User can create a bulk SMS campaign targeting one or more contact groups

CREATE TABLE campaigns (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    name          VARCHAR(255),
    body          TEXT        NOT NULL,
    sender_id     VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scheduled_at  TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaigns_user_id ON campaigns (user_id);

-- Partial index for scheduled campaign dispatcher (D-10)
CREATE INDEX idx_campaigns_status_scheduled ON campaigns (status, scheduled_at)
    WHERE status = 'SCHEDULED';

-- campaign_groups join table: supports MESG-01 (campaign targets one or more groups)
-- The group IDs reference the contact-service's contact_groups table (separate schema/service).
-- No FK constraint intentionally — cross-service reference managed at application layer.
CREATE TABLE campaign_groups (
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    group_id    UUID NOT NULL,
    PRIMARY KEY (campaign_id, group_id)
);
