package com.opendesk.messaging;

// Requirement: MESG-06
// Implementing plan: 04-05

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for campaign aggregate status tracking.
 *
 * <p>Wave 0 placeholder — test aborts immediately.
 * Implemented in plan 04-05 (Scheduled Campaigns + Campaign Status).
 */
class CampaignStatusIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-06: User can view campaign history with aggregate status (sent/delivered/failed counts).
     * After dispatching a campaign with stub provider: verify GET /api/v1/campaigns/{id}
     * returns correct aggregate counts (sentCount, deliveredCount, failedCount) derived
     * from outbound_messages table.
     */
    @Test
    void aggregateStatusCountsAreCorrect() {
        abort("Wave 0 placeholder — implement in 04-05");
    }
}
