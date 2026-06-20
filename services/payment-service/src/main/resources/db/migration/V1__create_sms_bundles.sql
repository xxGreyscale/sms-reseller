-- V1__create_sms_bundles.sql
-- payment-service schema — bundle catalog (PYMT-01, D-09, D-12)
-- Bundles are seeded via V2 migration; editable via admin UI (Phase 5) without code change.

CREATE TABLE sms_bundles (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name           VARCHAR(64) NOT NULL UNIQUE,
    sms_count      INT         NOT NULL,
    price_tzs      BIGINT      NOT NULL,   -- raw TZS whole shillings (D-11); 0 for Taster (FREE)
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    is_purchasable BOOLEAN     NOT NULL DEFAULT TRUE,  -- false for Taster (NIDA grant, not Azampay)
    description    VARCHAR(255)
);

COMMENT ON TABLE sms_bundles IS 'SMS bundle catalog — seeded by V2, editable via admin UI (Phase 5)';
COMMENT ON COLUMN sms_bundles.price_tzs IS 'Price in raw TZS whole shillings (D-11). 0 = free (Taster). No ×100 cents scaling.';
COMMENT ON COLUMN sms_bundles.is_purchasable IS 'false for Taster (granted on NIDA verify, not via Azampay STK push)';
