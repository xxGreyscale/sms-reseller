-- V1__create_credit_lots.sql (wallet-service)
-- Part of wallet schema — wallet-service owns this Flyway migration set.
-- Append-only credit lot table. Balance is always derived; no mutable balance column.

CREATE TABLE credit_lots (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL,
    lot_type    VARCHAR(20) NOT NULL,
    granted     INT         NOT NULL,
    consumed    INT         NOT NULL DEFAULT 0,
    reserved    INT         NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    payment_id  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index: only rows that still have available credits and have not expired.
-- Speeds up both the balance derivation query and the pessimistic reservation walk.
CREATE INDEX idx_credit_lots_user_expires
    ON credit_lots (user_id, expires_at)
    WHERE (granted - consumed - reserved) > 0;

COMMENT ON TABLE credit_lots IS 'Append-only credit lot ledger. One row per credit grant event.';
COMMENT ON COLUMN credit_lots.lot_type IS 'PURCHASED (12-month expiry) | BONUS (30-day) | REFUND';
COMMENT ON COLUMN credit_lots.granted IS 'SMS credits originally granted in this lot — immutable after insert';
COMMENT ON COLUMN credit_lots.consumed IS 'Credits fully debited from this lot by sent campaigns';
COMMENT ON COLUMN credit_lots.reserved IS 'Credits held for in-flight campaigns (SELECT FOR UPDATE pessimistic lock)';
COMMENT ON COLUMN credit_lots.payment_id IS 'FK to payments.id — null for BONUS and REFUND lots';
