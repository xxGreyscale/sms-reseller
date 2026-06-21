package com.opendesk.messaging;

// Requirement: MESG-06
// Implementing plan: 04-06

import com.opendesk.messaging.campaign.CampaignStatus;
import com.opendesk.messaging.contact.ContactRecipientClient;
import com.opendesk.messaging.message.MessageStatus;
import com.opendesk.messaging.message.OutboundMessage;
import com.opendesk.messaging.message.OutboundMessageRepository;
import com.opendesk.messaging.wallet.LotAllocation;
import com.opendesk.messaging.wallet.ReservationResult;
import com.opendesk.messaging.wallet.WalletReservationClient;
import org.awaitility.Awaitility;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for campaign aggregate status tracking (MESG-06).
 *
 * <p>After dispatch with mixed outcomes (1 normal ACCEPTED, 1 HARD_FAIL = suffix 0001),
 * the campaign's aggregate counts derived from outbound_messages must be accurate:
 * <ul>
 *   <li>sentCount = messages that reached SENT (or terminal DELIVERED)</li>
 *   <li>deliveredCount = messages that received a DLR DELIVERED</li>
 *   <li>failedCount = messages that reached FAILED</li>
 * </ul>
 *
 * <p>Once all messages reach a terminal state (DELIVERED or FAILED), the campaign becomes COMPLETED.
 */
class CampaignStatusIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @MockBean
    private WalletReservationClient walletReservationClient;

    @MockBean
    private ContactRecipientClient contactRecipientClient;

    /**
     * MESG-06: Campaign aggregate sent/delivered/failed counts equal per-message rows.
     * Campaign status becomes COMPLETED once all messages terminal.
     *
     * <p>Campaign: 1 normal recipient (ACCEPTED → DELIVERED via stub DLR) + 1 HARD_FAIL (suffix 0001).
     * Expected aggregate: total=2, sentCount=1, deliveredCount=1, failedCount=1.
     * Expected campaign status: COMPLETED once both messages terminal.
     */
    @Test
    void aggregateStatusCountsAreCorrect() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        UUID lotId = UUID.randomUUID();

        // 1 normal (ACCEPTED → DELIVERED) + 1 HARD_FAIL (suffix 0001)
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000005", "+255712000001"));

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
                "name", "Aggregate Status Test",
                "body", "Hello",
                "senderId", "TEST",
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

        // Await all messages reach terminal state (DELIVERED or FAILED)
        // The HARD_FAIL goes immediately to FAILED; the normal one hits DLR after ~100ms
        Awaitility.await("All messages reach terminal state")
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    List<OutboundMessage> messages = outboundMessageRepository.findByCampaignId(campaignId);
                    assertThat(messages).hasSize(2);
                    assertThat(messages).allMatch(m ->
                            m.getStatus() == MessageStatus.DELIVERED || m.getStatus() == MessageStatus.FAILED);
                });

        // GET /api/v1/campaigns/{id} → aggregate counts
        ResponseEntity<Map> campaignResponse = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(campaignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = campaignResponse.getBody();
        assertThat(body).isNotNull();

        // Aggregate counts must match per-message rows
        assertThat(((Number) body.get("totalCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("deliveredCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("failedCount")).intValue()).isEqualTo(1);

        // Campaign must be COMPLETED (all messages terminal)
        assertThat(body.get("status")).isEqualTo("COMPLETED");
    }
}
