package com.opendesk.contact;

// Requirement: CONT-04
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for contact group management.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-02 (Contact CRUD + Groups + Suppression).
 */
class ContactGroupIT extends AbstractContactIntegrationTest {

    /**
     * CONT-04: User can organize contacts into named groups.
     * Create group, add contacts, verify membership persisted; remove contact from group.
     */
    @Test
    void groupMembershipIsPersisted() {
        abort("Wave 0 placeholder — implement in 04-02");
    }
}
