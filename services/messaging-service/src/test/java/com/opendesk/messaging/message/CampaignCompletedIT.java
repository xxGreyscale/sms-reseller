package com.opendesk.messaging.message;

// Wave 0 RED placeholder — made GREEN by plan 05-02 (upstream gap fix: emit CampaignCompleted outbox event)
// Requirement: NOTF-05 prerequisite — CampaignCompleted event emitted by messaging-service

import com.opendesk.messaging.AbstractMessagingIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * RED placeholder: verifies that when all messages in a campaign reach terminal state,
 * DeliveryReceiptService emits a CampaignCompleted outbox row to messaging.events.
 *
 * <p>This tests the upstream gap fix (D-12): Phase 4 set campaign to COMPLETED in DB
 * but did NOT emit an outbox event. Plan 05-02 adds the outbox emit.
 *
 * <p>Will FAIL until plan 05-02 extends DeliveryReceiptService.checkCampaignCompletion()
 * to write a CampaignCompleted OutboxEntry.
 */
class CampaignCompletedIT extends AbstractMessagingIntegrationTest {

    @Test
    void campaignCompletionEmitsCampaignCompletedOutboxEvent() {
        Assumptions.abort("NOTF-05 upstream RED placeholder — production code absent (plan 05-02 makes this GREEN)");

        // When all messages for a campaign reach DELIVERED or FAILED status,
        // an OutboxEntry with eventType=CampaignCompleted must be persisted.
        assertThat(false).as("OutboxEntry for CampaignCompleted not yet emitted").isTrue();
    }
}
