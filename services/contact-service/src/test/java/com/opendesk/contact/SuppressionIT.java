package com.opendesk.contact;

// Requirement: CONT-08
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for suppression list management.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-02 (Contact CRUD + Groups + Suppression).
 */
class SuppressionIT extends AbstractContactIntegrationTest {

    /**
     * CONT-08: User can add numbers to suppression list; suppressed numbers excluded from all campaigns.
     * POST /api/v1/contacts/suppression with phone number;
     * verify number appears in GET /api/v1/contacts/suppression;
     * verify campaign expansion excludes suppressed numbers.
     */
    @Test
    void suppressedNumberIsExcluded() {
        abort("Wave 0 placeholder — implement in 04-02");
    }
}
