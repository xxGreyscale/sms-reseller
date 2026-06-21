package com.opendesk.messaging;

// Requirement: MESG-07
// Implementing plan: 04-06

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
 * Integration tests for per-message delivery status tracking (MESG-07).
 *
 * <p>StubSmsProvider fires a DLR after dlr-delay-ms (100ms in test profile), which triggers
 * DeliveryReceiptService.handleDeliveryReceipt(externalId, status). The outbound_message
 * transitions PENDING → SENT → DELIVERED.
 *
 * <p>Per-message status is also queryable via GET /api/v1/campaigns/{id}/messages (MESG-07).
 */
class DeliveryTrackingIT extends AbstractMessagingIntegrationTest {

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
     * MESG-07: Per-message status transitions PENDING → SENT → DELIVERED via stub DLR.
     *
     * <p>Steps:
     * <ol>
     *   <li>Dispatch a campaign with 1 normal recipient (ACCEPTED by stub).</li>
     *   <li>Await SENT state (SendMessageConsumer processes the AMQP message).</li>
     *   <li>Stub DLR fires after 100ms → DeliveryReceiptService updates SENT → DELIVERED.</li>
     *   <li>GET /api/v1/campaigns/{id}/messages → 200 with message status=DELIVERED.</li>
     * </ol>
     */
    @Test
    void perMessageStatusTransitions() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        UUID lotId = UUID.randomUUID();

        // 1 normal recipient (ACCEPTED by stub → then DLR DELIVERED)
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000005"));

        when(walletReservationClient.reserve(any(), anyInt(), any()))
                .thenReturn(new ReservationResult(
                        List.of(lotId),
                        1,
                        List.of(new LotAllocation(lotId, 1))
                ));

        // Create campaign
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> createBody = Map.of(
                "name", "Delivery Tracking Test",
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

        // Await DELIVERED (stub DLR fires after 100ms; StubSmsProvider sweep every 5s but the
        // Scheduled method runs on the Spring scheduler — allow up to 15s total)
        Awaitility.await("Message reaches DELIVERED via stub DLR")
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    List<OutboundMessage> messages = outboundMessageRepository.findByCampaignId(campaignId);
                    assertThat(messages).hasSize(1);
                    assertThat(messages.get(0).getStatus()).isEqualTo(MessageStatus.DELIVERED);
                });

        // MESG-07: GET /api/v1/campaigns/{id}/messages returns per-message status
        ResponseEntity<List> messagesResponse = restTemplate.exchange(
                "/api/v1/campaigns/" + campaignId + "/messages",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );
        assertThat(messagesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(messagesResponse.getBody()).hasSize(1);

        Map<?, ?> messageView = (Map<?, ?>) messagesResponse.getBody().get(0);
        assertThat(messageView.get("status")).isEqualTo("DELIVERED");
        assertThat(messageView.get("phoneE164")).isEqualTo("+255712000005");
    }
}
