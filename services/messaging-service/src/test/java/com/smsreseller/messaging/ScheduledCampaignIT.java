package com.smsreseller.messaging;

// Requirements: MESG-04, MESG-05
// Implementing plan: 04-08

import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignStatus;
import com.smsreseller.messaging.contact.ContactRecipientClient;
import com.smsreseller.messaging.message.OutboundMessageRepository;
import com.smsreseller.messaging.scheduler.ScheduledCampaignDispatchJob;
import com.smsreseller.messaging.wallet.InsufficientCreditsException;
import com.smsreseller.messaging.wallet.LotAllocation;
import com.smsreseller.messaging.wallet.ReservationResult;
import com.smsreseller.messaging.wallet.WalletReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for scheduled campaign dispatch (MESG-04, MESG-05).
 *
 * <p>Uses the testable {@code dispatch(Instant now)} delegate on
 * {@link ScheduledCampaignDispatchJob} to fast-forward time without waiting for
 * the real {@code @Scheduled} timer.
 */
class ScheduledCampaignIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @Autowired
    private ScheduledCampaignDispatchJob scheduledCampaignDispatchJob;

    @MockBean
    private WalletReservationClient walletReservationClient;

    @MockBean
    private ContactRecipientClient contactRecipientClient;

    /**
     * MESG-04: Poller dispatches a SCHEDULED campaign at/after scheduledAt.
     *
     * <p>Scenario:
     * <ol>
     *   <li>POST /api/v1/campaigns with {@code scheduledAt} = now - 1 minute → campaign in SCHEDULED state.</li>
     *   <li>Call dispatch(now) → poller finds it (scheduledAt < now).</li>
     *   <li>Campaign transitions to QUEUED; outbound messages are persisted.</li>
     *   <li>A campaign scheduled for the future (scheduledAt = now + 1h) is NOT dispatched.</li>
     * </ol>
     */
    @Test
    void pollerDispatchesAtScheduledTime() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000010", "+255712000011"));
        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenReturn(new ReservationResult(
                        List.of(lotId), 2, List.of(new LotAllocation(lotId, 2))
                ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Campaign scheduled 1 minute in the past (due now)
        Instant scheduledAt = Instant.now().minus(1, ChronoUnit.MINUTES);

        Map<String, Object> body = Map.of(
                "name", "Scheduled Campaign",
                "body", "Hello scheduled members",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(groupId.toString()),
                "scheduledAt", scheduledAt.toString()
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString(createResponse.getBody().get("id").toString());

        // Verify campaign is SCHEDULED before dispatch
        var campaignBefore = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaignBefore.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);

        // Campaign scheduled far in the future — should NOT be dispatched
        Map<String, Object> futureBody = Map.of(
                "name", "Future Campaign",
                "body", "Hello future",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(groupId.toString()),
                "scheduledAt", Instant.now().plus(1, ChronoUnit.HOURS).toString()
        );

        ResponseEntity<Map> futureResponse = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(futureBody, headers),
                Map.class
        );
        assertThat(futureResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID futureCampaignId = UUID.fromString(futureResponse.getBody().get("id").toString());

        // Fast-forward: call dispatch(now) — triggers the poller logic
        scheduledCampaignDispatchJob.dispatch(Instant.now());

        // Due campaign must be QUEUED now
        var campaignAfter = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaignAfter.getStatus()).isEqualTo(CampaignStatus.QUEUED);

        // Outbound messages must be persisted
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).hasSize(2);

        // Future campaign must remain SCHEDULED
        var futureCampaignAfter = campaignRepository.findById(futureCampaignId).orElseThrow();
        assertThat(futureCampaignAfter.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
    }

    /**
     * MESG-05: Cancelled campaign is never dispatched by the poller.
     *
     * <p>Scenario:
     * <ol>
     *   <li>Create a SCHEDULED campaign.</li>
     *   <li>Cancel it via POST /api/v1/campaigns/{id}/cancel → status CANCELLED.</li>
     *   <li>Call dispatch(now) → poller skips CANCELLED campaigns.</li>
     *   <li>Status remains CANCELLED; no outbound messages created.</li>
     * </ol>
     */
    @Test
    void cancelledCampaignNotDispatched() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create a campaign scheduled 1 minute in the past
        Instant scheduledAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        Map<String, Object> body = Map.of(
                "name", "To Be Cancelled",
                "body", "Cancelled campaign",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(groupId.toString()),
                "scheduledAt", scheduledAt.toString()
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString(createResponse.getBody().get("id").toString());

        // Cancel the campaign
        ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(cancelResponse.getStatusCode().value()).isIn(200, 204);

        // Verify CANCELLED before dispatch
        var campaignAfterCancel = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaignAfterCancel.getStatus()).isEqualTo(CampaignStatus.CANCELLED);

        // Dispatch — poller must skip CANCELLED campaigns
        scheduledCampaignDispatchJob.dispatch(Instant.now());

        // Status must remain CANCELLED
        var campaignAfterDispatch = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaignAfterDispatch.getStatus()).isEqualTo(CampaignStatus.CANCELLED);

        // No outbound_messages persisted for this campaign
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).isEmpty();
    }
}
