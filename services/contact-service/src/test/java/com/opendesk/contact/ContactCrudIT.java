package com.opendesk.contact;

// Requirements: CONT-01, CONT-02, CONT-03
// Implementing plan: 04-02

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for contact CRUD operations.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-02 (Contact CRUD + Groups + Suppression).
 */
class ContactCrudIT extends AbstractContactIntegrationTest {

    /**
     * CONT-01: User can add individual contacts manually (name + phone number).
     * POST /api/v1/contacts → 201; contact retrievable via GET.
     */
    @Test
    void addContactPersistsAndIsRetrievable() {
        abort("Wave 0 placeholder — implement in 04-02");
    }

    /**
     * CONT-02: User can edit existing contacts (name and/or phone).
     * PATCH /api/v1/contacts/{id} → 200; updated fields returned; IDOR guard via JWT sub.
     */
    @Test
    void editContactUpdatesFields() {
        abort("Wave 0 placeholder — implement in 04-02");
    }

    /**
     * CONT-03: User can delete contacts; contact removed from list and all groups.
     * DELETE /api/v1/contacts/{id} → 204; contact no longer retrievable.
     */
    @Test
    void deleteContactRemovesFromGroups() {
        abort("Wave 0 placeholder — implement in 04-02");
    }
}
