-- V2__create_credit_transactions.sql (wallet-service)
-- Immutable append-only audit trail for every credit movement.
-- No UPDATE or DELETE ever issued against this table.

CREATE TABLE credit_transactions (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID        NOT NULL,
    lot_id        UUID        NOT NULL REFERENCES credit_lots(id),
    txn_type      VARCHAR(20) NOT NULL,
    delta         INT         NOT NULL,
    reference_id  UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_txn_user_created
    ON credit_transactions (user_id, created_at DESC);

COMMENT ON TABLE credit_transactions IS 'Append-only ledger entries. One row per credit movement event on a lot.';
COMMENT ON COLUMN credit_transactions.txn_type IS 'GRANT | RESERVE | CONSUME | RELEASE | EXPIRE | REFUND';
COMMENT ON COLUMN credit_transactions.delta IS 'Always positive; txn_type conveys direction (GRANT adds, CONSUME removes, etc.)';
COMMENT ON COLUMN credit_transactions.reference_id IS 'campaign_id for RESERVE/CONSUME, payment_id for GRANT/REFUND — nullable for expiry events';
