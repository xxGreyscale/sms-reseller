package com.opendesk.messaging;

// Requirements: MESG-01, MESG-03, MESG-08
// Implementing plan: 04-04

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for campaign creation and dispatch.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-04 (Campaign Create + Send Pipeline).
 */
class CampaignIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-01: User can create a bulk SMS campaign targeting one or more contact groups.
     * POST /api/v1/campaigns with group IDs → 201; campaign in DRAFT state.
     */
    @Test
    void createCampaignTargetingGroups() {
        abort("Wave 0 placeholder — implement in 04-04");
    }

    /**
     * MESG-03: System reserves credits before campaign QUEUED; refuses with clear error if insufficient.
     * Attempt to send campaign when wallet has insufficient credits → 402/409 response;
     * campaign remains in DRAFT or FAILED state; no AMQP messages published.
     */
    @Test
    void insufficientCreditsBlocksQueuedTransition() {
        abort("Wave 0 placeholder — implement in 04-04");
    }

    /**
     * MESG-08: User sees post-send confirmation including credits deducted and messages queued.
     * POST /api/v1/campaigns/{id}/send → 202 with CampaignDispatchResponse containing
     * campaignId, recipientCount, and creditsReserved.
     */
    @Test
    void dispatchResponseIncludesCreditsAndCount() {
        abort("Wave 0 placeholder — implement in 04-04");
    }
}
