package com.opendesk.messaging;

// Requirement: MESG-09
// Implementing plan: 04-06

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for the SMS send pipeline (fan-out, suppression filtering).
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-06 (Send Pipeline + DLX Retry + Delivery Tracking).
 */
class SendPipelineIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-09: System automatically excludes suppressed numbers from campaign recipients.
     * Set up a campaign targeting a group that includes a suppressed number;
     * dispatch the campaign;
     * verify that no AMQP message is published to messaging.send for the suppressed number
     * (i.e. outbound_messages count = total recipients - suppressed count).
     */
    @Test
    void suppressedNumberNotPublished() {
        abort("Wave 0 placeholder — implement in 04-06");
    }
}
