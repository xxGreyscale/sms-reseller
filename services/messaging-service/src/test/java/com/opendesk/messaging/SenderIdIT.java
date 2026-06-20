package com.opendesk.messaging;

// Requirements: SNDR-02, SNDR-03, SNDR-04
// Implementing plan: 04-08

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for sender-ID request state machine.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-08 (Sender-ID Request + State Machine).
 */
class SenderIdIT extends AbstractMessagingIntegrationTest {

    /**
     * SNDR-02: User can request a custom alphanumeric sender ID.
     * POST /api/v1/sender-ids/requests with name → 201;
     * GET /api/v1/sender-ids/requests returns request with status=REQUESTED.
     */
    @Test
    void userCanSubmitRequest() {
        abort("Wave 0 placeholder — implement in 04-08");
    }

    /**
     * SNDR-03: Admin can approve or reject sender ID requests.
     * POST /api/v1/internal/sender-ids/{id}/approve with ROLE_ADMIN JWT;
     * verify request status transitions to APPROVED.
     */
    @Test
    void adminApproveTransitionsToApproved() {
        abort("Wave 0 placeholder — implement in 04-08");
    }

    /**
     * SNDR-04: User receives notification when sender ID approved/rejected.
     * After admin approval, verify SenderIdDecided outbox entry exists with
     * routing key messaging.SenderIdDecided on messaging.events exchange.
     */
    @Test
    void senderIdDecidedEventPublished() {
        abort("Wave 0 placeholder — implement in 04-08");
    }
}
