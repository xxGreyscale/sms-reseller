package com.opendesk.messaging;

// Requirements: MESG-04, MESG-05
// Implementing plan: 04-05

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Integration tests for scheduled campaign dispatch.
 *
 * <p>Wave 0 placeholders — all tests abort immediately.
 * Implemented in plan 04-05 (Scheduled Campaigns + Campaign Status).
 */
class ScheduledCampaignIT extends AbstractMessagingIntegrationTest {

    /**
     * MESG-04: User can schedule a campaign for a specific future date/time; poller dispatches at/after time.
     * Create campaign with scheduledAt=future; advance clock past scheduledAt;
     * invoke ScheduledCampaignDispatchJob.dispatch(now); verify campaign transitions to QUEUED/DISPATCHING.
     */
    @Test
    void pollerDispatchesAtScheduledTime() {
        abort("Wave 0 placeholder — implement in 04-05");
    }

    /**
     * MESG-05: User can cancel a scheduled campaign before dispatch; poller skips it.
     * Create campaign with scheduledAt=future; cancel it; invoke dispatch; verify campaign stays CANCELLED.
     */
    @Test
    void cancelledCampaignNotDispatched() {
        abort("Wave 0 placeholder — implement in 04-05");
    }
}
