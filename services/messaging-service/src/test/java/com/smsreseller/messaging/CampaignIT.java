package com.smsreseller.messaging;

// Requirements: MESG-01, MESG-03, MESG-08
// Implementing plan: 04-05

import com.smsreseller.messaging.campaign.CampaignDispatchResponse;
import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignStatus;
import com.smsreseller.messaging.contact.ContactRecipientClient;
import com.smsreseller.messaging.message.OutboundMessageRepository;
import com.smsreseller.messaging.wallet.WalletReservationClient;
import com.smsreseller.messaging.wallet.ReservationResult;
import com.smsreseller.messaging.wallet.LotAllocation;
import com.smsreseller.messaging.wallet.InsufficientCreditsException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for campaign creation and dispatch.
 */
class CampaignIT extends AbstractMessagingIntegrationTest {

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

    /**
     * MESG-01: User can create a bulk SMS campaign targeting one or more contact groups.
     * POST /api/v1/campaigns with group IDs → 201; campaign in DRAFT state.
     */
    @Test
    void createCampaignTargetingGroups() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTestHelper.createToken(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "name", "Test Campaign",
                "body", "Hello from sms-reseller",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(UUID.randomUUID().toString())
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("status")).isEqualTo("DRAFT");
        assertThat(responseBody.get("id")).isNotNull();
    }

    /**
     * MESG-03: System reserves credits before campaign QUEUED; refuses with clear error if insufficient.
     * When wallet returns 409 (insufficient credits), campaign stays NOT QUEUED and no messages queued.
     */
    @Test
    void insufficientCreditsBlocksQueuedTransition() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        // ContactRecipientClient returns 2 recipients
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000001", "+255712000002"));

        // WalletReservationClient throws InsufficientCreditsException (simulates 409)
        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenThrow(new InsufficientCreditsException("Insufficient credits"));

        // Create campaign
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> createBody = Map.of(
                "name", "No Credits Campaign",
                "body", "Hello",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(groupId.toString())
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(createBody, headers),
                Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString(createResponse.getBody().get("id").toString());

        // Attempt dispatch — should fail with insufficient credits
        ResponseEntity<Map> sendResponse = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/send",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        // Should return 402 or 409
        assertThat(sendResponse.getStatusCode().value())
                .isIn(402, 409);

        // Campaign must NOT be QUEUED
        var campaign = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaign.getStatus()).isNotEqualTo(CampaignStatus.QUEUED);

        // No outbound_messages persisted
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).isEmpty();
    }

    /**
     * MESG-08: User sees post-send confirmation including credits reserved and messages queued.
     */
    @Test
    void dispatchResponseIncludesCreditsAndCount() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        UUID lotId = UUID.randomUUID();
        // ContactRecipientClient returns 2 recipients
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000003", "+255712000004"));

        // WalletReservationClient succeeds with 2 credits from one lot
        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenReturn(new ReservationResult(
                        List.of(lotId),
                        2,
                        List.of(new LotAllocation(lotId, 2))
                ));

        // Create campaign
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> createBody = Map.of(
                "name", "Dispatch Campaign",
                "body", "Hello members",
                "senderId", "SMSRESELLER",
                "groupIds", List.of(groupId.toString())
        );

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/v1/campaigns",
                HttpMethod.POST,
                new HttpEntity<>(createBody, headers),
                Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString(createResponse.getBody().get("id").toString());

        // Dispatch
        ResponseEntity<Map> sendResponse = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/send",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> dispatchBody = sendResponse.getBody();
        assertThat(dispatchBody).isNotNull();
        assertThat(dispatchBody.get("campaignId")).isEqualTo(campaignId.toString());
        assertThat(((Number) dispatchBody.get("recipientCount")).intValue()).isEqualTo(2);
        assertThat(((Number) dispatchBody.get("creditsReserved")).intValue()).isEqualTo(2);

        // Campaign must be QUEUED
        var campaign = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.QUEUED);

        // Two outbound_messages with non-null lotId
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).hasSize(2);
        messages.forEach(m -> assertThat(m.getLotId()).isNotNull());
    }
}
