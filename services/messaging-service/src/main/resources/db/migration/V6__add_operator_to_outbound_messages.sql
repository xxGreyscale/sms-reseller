-- V6: Add operator column to outbound_messages (D-13)
-- Stores the derived TZ MNO label (Vodacom, Tigo, Airtel, Halotel, UNKNOWN) at dispatch time.
-- Required for ANLX-03 GROUP BY operator analytics.

ALTER TABLE outbound_messages
    ADD COLUMN IF NOT EXISTS operator VARCHAR(50);

-- Backfill existing rows by deriving operator from phone_e164 prefix.
-- TZ MNO prefix ranges (E.164 +255 + 2-digit sub-prefix):
--   Vodacom/M-Pesa: 074x, 075x, 076x
--   Tigo/Miamungu:  071x, 065x, 067x
--   Airtel:         078x, 079x, 068x, 069x
--   Halotel:        062x
-- Anything else → 'UNKNOWN'
UPDATE outbound_messages
SET operator = CASE
    WHEN phone_e164 LIKE '+25574%' THEN 'Vodacom'
    WHEN phone_e164 LIKE '+25575%' THEN 'Vodacom'
    WHEN phone_e164 LIKE '+25576%' THEN 'Vodacom'
    WHEN phone_e164 LIKE '+25571%' THEN 'Tigo'
    WHEN phone_e164 LIKE '+25565%' THEN 'Tigo'
    WHEN phone_e164 LIKE '+25567%' THEN 'Tigo'
    WHEN phone_e164 LIKE '+25578%' THEN 'Airtel'
    WHEN phone_e164 LIKE '+25579%' THEN 'Airtel'
    WHEN phone_e164 LIKE '+25568%' THEN 'Airtel'
    WHEN phone_e164 LIKE '+25569%' THEN 'Airtel'
    WHEN phone_e164 LIKE '+25562%' THEN 'Halotel'
    ELSE 'UNKNOWN'
END
WHERE operator IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbound_messages_operator ON outbound_messages (user_id, operator);
