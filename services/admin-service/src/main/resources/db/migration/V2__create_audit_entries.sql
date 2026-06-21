-- V2__create_audit_entries.sql
-- Append-only audit log for ADMN-06 (D-09 dual-source: mutations + domain events).
-- No UPDATE or DELETE statements ever touch this table — enforced at the repository level.

CREATE TABLE IF NOT EXISTS admin.audit_entries (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       VARCHAR(255) NOT NULL,  -- admin email OR "system" for event-driven rows
    action      VARCHAR(255) NOT NULL,  -- monospace label e.g. SENDER_ID_APPROVED, UserVerified
    target      VARCHAR(255),           -- aggregate id or resource identifier
    details     JSONB,                  -- optional structured payload (UI-SPEC: Details column)

    CONSTRAINT audit_entries_pkey PRIMARY KEY (id)
);

-- Index for the viewer's default sort (newest-first) and date-range filter
CREATE INDEX IF NOT EXISTS idx_audit_entries_timestamp_desc
    ON admin.audit_entries (timestamp DESC);

-- Index for actor filter (UI-SPEC: actor filter)
CREATE INDEX IF NOT EXISTS idx_audit_entries_actor
    ON admin.audit_entries (actor);
