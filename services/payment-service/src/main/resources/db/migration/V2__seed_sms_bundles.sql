-- V2__seed_sms_bundles.sql
-- payment-service — seed the locked MVP bundle catalog (D-09, D-11)
--
-- Prices are raw TZS whole shillings per D-11 (supersedes any ×100 cents convention).
-- Source: .planning/PROJECT.md §"Pricing Model" (LOCKED).
--
-- Bundle    | SMS    | Price TZS | Purchasable
-- --------- | ------ | --------- | -----------
-- Taster    |  50    |    0      | false (NIDA-verify grant — not via Azampay)
-- Starter   | 200    | 3200      | true
-- Growth    | 1000   | 14500     | true
-- Pro       | 5000   | 65000     | true
-- Scale     | 20000  | 240000    | true

INSERT INTO sms_bundles (name, sms_count, price_tzs, is_active, is_purchasable, description)
VALUES
    ('Taster',  50,    0,      TRUE, FALSE, 'Free on NIDA verification — not purchasable via Azampay'),
    ('Starter', 200,   3200,   TRUE, TRUE,  NULL),
    ('Growth',  1000,  14500,  TRUE, TRUE,  NULL),
    ('Pro',     5000,  65000,  TRUE, TRUE,  NULL),
    ('Scale',   20000, 240000, TRUE, TRUE,  NULL)
ON CONFLICT (name) DO NOTHING;
