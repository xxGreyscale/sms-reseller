package com.opendesk.identity.user;

/**
 * Discriminates platform users from operator admin accounts.
 *
 * <p>ADMIN accounts are seeded via Flyway V5 migration (D-02) and cannot self-register.
 * USER is the default for all self-registered accounts.
 *
 * <p>Used by AdminLoginService to assert that only ADMIN role accounts can obtain
 * admin JWTs (T-05-05 mitigation).
 */
public enum UserRole {
    USER,
    ADMIN
}
