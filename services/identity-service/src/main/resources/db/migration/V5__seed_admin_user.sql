-- V5__seed_admin_user.sql
-- Seeds the operator admin account (D-02, ADMN-01).
-- Password BCrypt hash injected via Flyway placeholder ${adminPasswordHash} — value from K8s Secret.
-- Do NOT hardcode a real hash in VCS.
--
-- Flyway placeholder substitution requires:
--   flyway.placeholder-replacement=true (default true)
--   flyway.placeholders.adminEmail      → from env ADMIN_EMAIL
--   flyway.placeholders.adminPasswordHash → from env ADMIN_PASSWORD_HASH (BCrypt hash, e.g. {bcrypt}$2a$...)
--
-- ON CONFLICT DO NOTHING: safe to re-run; idempotent (T-05-07 mitigated — no hardcoded hash).
INSERT INTO users (id, email, phone, full_name, password_hash, role, status, created_at)
VALUES (
    gen_random_uuid(),
    '${adminEmail}',
    NULL,
    'Platform Admin',
    '${adminPasswordHash}',
    'ADMIN',
    'VERIFIED',
    now()
)
ON CONFLICT (email) DO NOTHING;
