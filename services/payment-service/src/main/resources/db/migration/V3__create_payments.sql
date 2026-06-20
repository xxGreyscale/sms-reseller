-- V3__create_payments.sql
-- payment-service — payments table + single-pending-per-user enforcement (D-05, D-13)

CREATE TABLE payments (
    id                  UUID        NOT NULL PRIMARY KEY,
    user_id             UUID        NOT NULL,
    bundle_id           UUID        NOT NULL,
    amount_tzs          BIGINT      NOT NULL,   -- raw TZS whole shillings (D-11)
    sms_count           INT         NOT NULL,
    status              VARCHAR(20) NOT NULL,   -- PENDING | SUCCESS | EXPIRED | FAILED (D-06)
    external_id         VARCHAR(128) UNIQUE,    -- Azampay idempotency key = payment UUID string
    operator_reference  VARCHAR(128),           -- Azampay operator transaction reference (nullable)
    provider            VARCHAR(64) NOT NULL,   -- mobile money provider (e.g. "MPESA", "TIGOPESA")
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN payments.external_id IS 'Azampay idempotency key = payment UUID string. UNIQUE constraint prevents duplicate STK pushes.';
COMMENT ON COLUMN payments.amount_tzs IS 'Amount in raw TZS whole shillings (D-11). Copied from bundle at purchase time — no FK join needed.';
COMMENT ON COLUMN payments.status IS 'State machine: PENDING → SUCCESS | EXPIRED | FAILED (D-06). EXPIRED may → SUCCESS via reconciliation (D-04).';

-- D-05 / D-13: Enforce one PENDING payment per user via partial unique index.
-- A second INSERT with status=PENDING for the same user_id will fail with a unique constraint violation.
-- This is transactionally correct and simpler than a Redis lock.
CREATE UNIQUE INDEX uq_payments_user_pending
    ON payments (user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_payments_user_created
    ON payments (user_id, created_at DESC);

CREATE INDEX idx_payments_status_created
    ON payments (status, created_at)
    WHERE status IN ('PENDING', 'EXPIRED');
