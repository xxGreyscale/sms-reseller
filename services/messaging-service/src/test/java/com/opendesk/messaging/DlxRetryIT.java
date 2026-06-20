package com.opendesk.messaging;

// Requirement: MESG-10
// Implementing plan: 04-06

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for DLX retry ladder and permanent failure handling.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-06 (Send Pipeline + DLX Retry + Delivery Tracking).
 *
 * <p>Note: Uses shortened TTL ladder from application-test.yml (2s/4s/6s vs 60s/300s/900s prod)
 * so the test completes within the 180s feedback budget.
 */
class DlxRetryIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-10: System retries failed messages via DLQ; refunds credits for permanently undeliverable messages.
     * Configure StubSmsProvider to HARD_FAIL for a specific phone suffix;
     * publish one AMQP message to messaging.send;
     * verify message is nacked → DLX → retry queues → dead queue (delivery limit exhausted);
     * verify DeadLetterConsumer updates OutboundMessage to FAILED;
     * verify MessageRefundDue event is written to the outbox.
     */
    @Test
    void permanentFailureEmitsRefundDueEvent() {
        abort("Wave 0 placeholder — implement in 04-06");
    }
}
