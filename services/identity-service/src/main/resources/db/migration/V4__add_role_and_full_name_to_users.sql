-- V4: Add role and full_name columns to users; relax phone nullable constraint.
--
-- Role discriminates self-registered USER accounts from seeded operator ADMIN accounts (D-02).
-- full_name stores the user's display name (for admin user search UI columns — ADMN-02).
-- Phone is made nullable to allow the seeded admin account (which has no phone) to be inserted
-- via V5 migration without violating the NOT NULL constraint.

-- 1. Add role column with USER default so existing rows are backfilled correctly.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 2. Add full_name column (nullable — not yet collected for existing registrations).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);

-- 3. Relax phone NOT NULL constraint — admin accounts have no phone.
--    Self-registered user validation is enforced at the application layer (RegistrationService).
ALTER TABLE users
    ALTER COLUMN phone DROP NOT NULL;
