package com.opendesk.messaging;

// Requirement: MESG-07
// Implementing plan: 04-06

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for per-message delivery status tracking.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-06 (Send Pipeline + DLX Retry + Delivery Tracking).
 */
class DeliveryTrackingIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-07: User can view per-message delivery status within a campaign.
     * Send a campaign; await StubSmsProvider DLR simulation;
     * verify outbound_messages transitions: PENDING → SENT → DELIVERED.
     * Verify GET /api/v1/campaigns/{id}/messages returns per-message status.
     */
    @Test
    void perMessageStatusTransitions() {
        abort("Wave 0 placeholder — implement in 04-06");
    }
}
