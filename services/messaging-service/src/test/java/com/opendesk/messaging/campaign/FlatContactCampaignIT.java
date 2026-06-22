package com.opendesk.messaging.campaign;

// Requirement: MOBL-07
// Implementing plan: 06-03

import com.opendesk.messaging.AbstractMessagingIntegrationTest;
import com.opendesk.messaging.JwtTestHelper;
import com.opendesk.messaging.contact.ContactRecipientClient;
import com.opendesk.messaging.message.OutboundMessageRepository;
import com.opendesk.messaging.wallet.InsufficientCreditsException;
import com.opendesk.messaging.wallet.LotAllocation;
import com.opendesk.messaging.wallet.ReservationResult;
import com.opendesk.messaging.wallet.WalletReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for flat-contact campaign targeting via contactIds[] (D-12, MOBL-07).
 *
 * <p>Tests:
 * <ol>
 *   <li>Campaign created with contactIds (no groupIds) dispatches successfully: recipients
 *       expanded, credits reserved, campaign reaches QUEUED, OutboundMessages persisted.</li>
 *   <li>Campaign with neither groupIds nor contactIds is rejected (validation guard).</li>
 *   <li>Existing groupIds[] path still works (regression — group expansion unchanged).</li>
 * </ol>
 *
 * <p>ContactRecipientClient is @MockBean — no HTTP to contact-service in these ITs.
 */
class FlatContactCampaignIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @MockBean
    private WalletReservationClient walletReservationClient;

    @MockBean
    private ContactRecipientClient contactRecipientClient;

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /**
     * Test 1: POST campaign with contactIds[] (no groupIds) then send → recipients expand,
     * credits reserved, campaign QUEUED, OutboundMessages persisted (D-12, MOBL-07).
     */
    @Test
    void flatContactCampaignDispatchesSuccessfully() {
        UUID userId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());
        UUID contactId1 = UUID.randomUUID();
        UUID contactId2 = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        // getRecipientsByContactIds is called during executeSend for the flat-contact path
        when(contactRecipientClient.getRecipientsByContactIds(any(), any()))
                .thenReturn(List.of("+255712002001", "+255712002002"));

        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenReturn(new ReservationResult(
                        List.of(lotId),
                        2,
                        List.of(new LotAllocation(lotId, 2))
                ));

        // Create campaign with contactIds only (no groupIds)
        Map<String, Object> createBody = Map.of(
                "name", "Flat Contact Campaign",
                "body", "Hello from flat-contact",
                "senderId", "OPENDESK",
                "contactIds", List.of(contactId1.toString(), contactId2.toString())
        );

        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(createBody, bearerHeaders(token)),
                Map.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString((String) createResp.getBody().get("id"));
        assertThat(createResp.getBody().get("status")).isEqualTo("DRAFT");

        // Dispatch
        ResponseEntity<Map> sendResp = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/send",
                HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(token)),
                Map.class
        );

        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) sendResp.getBody().get("recipientCount")).intValue()).isEqualTo(2);
        assertThat(((Number) sendResp.getBody().get("creditsReserved")).intValue()).isEqualTo(2);

        // Campaign must be QUEUED
        var campaign = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.QUEUED);

        // Two OutboundMessages persisted
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).hasSize(2);
    }

    /**
     * Test 2: Campaign with neither groupIds nor contactIds is rejected.
     * Validation guard prevents 0-recipient campaigns from being created (T-06-03-03).
     */
    @Test
    void campaignWithNoTargetIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        Map<String, Object> createBody = Map.of(
                "name", "Empty Target Campaign",
                "body", "Hello",
                "senderId", "OPENDESK"
                // no groupIds, no contactIds
        );

        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(createBody, bearerHeaders(token)),
                Map.class
        );

        // Must be rejected — 400 (validation) or 422 or 409
        assertThat(createResp.getStatusCode().is4xxClientError()).isTrue();
    }

    /**
     * Test 3: Existing groupIds[] path still works unchanged (regression).
     * Mirrors CampaignIT.createCampaignTargetingGroups + dispatchResponseIncludesCreditsAndCount.
     */
    @Test
    void groupIdsCampaignStillDispatches() {
        UUID userId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());
        UUID groupId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712002003", "+255712002004"));

        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenReturn(new ReservationResult(
                        List.of(lotId),
                        2,
                        List.of(new LotAllocation(lotId, 2))
                ));

        Map<String, Object> createBody = Map.of(
                "name", "Group Campaign Regression",
                "body", "Regression test message",
                "senderId", "OPENDESK",
                "groupIds", List.of(groupId.toString())
        );

        ResponseEntity<Map> createResp = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(createBody, bearerHeaders(token)),
                Map.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString((String) createResp.getBody().get("id"));

        ResponseEntity<Map> sendResp = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/send",
                HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(token)),
                Map.class
        );

        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var campaign = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.QUEUED);
    }
}
