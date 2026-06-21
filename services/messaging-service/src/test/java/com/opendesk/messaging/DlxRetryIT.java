package com.opendesk.messaging;

// Requirement: MESG-10
// Implementing plan: 04-06

import com.opendesk.messaging.campaign.Campaign;
import com.opendesk.messaging.campaign.CampaignRepository;
import com.opendesk.messaging.campaign.CampaignStatus;
import com.opendesk.messaging.contact.ContactRecipientClient;
import com.opendesk.messaging.message.MessageStatus;
import com.opendesk.messaging.message.OutboundMessage;
import com.opendesk.messaging.message.OutboundMessageRepository;
import com.opendesk.messaging.message.SendMessagePayload;
import com.opendesk.messaging.outbox.OutboxRepository;
import com.opendesk.messaging.wallet.LotAllocation;
import com.opendesk.messaging.wallet.ReservationResult;
import com.opendesk.messaging.wallet.WalletReservationClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * Integration tests for DLX retry ladder and permanent failure handling (MESG-10).
 *
 * <p>Uses shortened TTL ladder from application-test.yml (2s/4s/6s) so the test completes
 * well within the 180s feedback budget. The quorum queue deliveryLimit(3) + DLX topology
 * routes exhausted messages to messaging.dead, where DeadLetterConsumer writes FAILED + refund.
 *
 * <p>Two scenarios:
 * <ul>
 *   <li>Suffix 0001 (HARD_FAIL): goes directly to dead queue without traversing the retry ladder</li>
 *   <li>Suffix 0002 (TRANSIENT_FAIL): exhausts the 3-retry DLX ladder then lands in dead queue</li>
 * </ul>
 */
class DlxRetryIT extends AbstractMessagingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    @Autowired
    private OutboundMessageRepository outboundMessageRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockBean
    private WalletReservationClient walletReservationClient;

    @MockBean
    private ContactRecipientClient contactRecipientClient;

    /**
     * MESG-10: Permanent failure (HARD_FAIL or retries exhausted) emits MessageRefundDue.
     *
     * <p>Test strategy:
     * <ol>
     *   <li>Dispatch a campaign with 1 HARD_FAIL recipient (suffix 0001) + 1 TRANSIENT_FAIL (suffix 0002).</li>
     *   <li>The HARD_FAIL message is nacked immediately → DeadLetterConsumer marks it FAILED
     *       and writes a MessageRefundDue outbox entry.</li>
     *   <li>The TRANSIENT_FAIL message traverses the 2s/4s/6s TTL ladder (3 retries via
     *       deliveryLimit), then lands in messaging.dead → DeadLetterConsumer marks it FAILED +
     *       writes another MessageRefundDue entry.</li>
     *   <li>Both outbound_messages end in FAILED; two MessageRefundDue rows are written (unique eventIds).</li>
     * </ol>
     *
     * <p>Total expected wait: ~12s (ladder total) + margin. Awaitility budget: 60s.
     */
    @Test
    void permanentFailureEmitsRefundDueEvent() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        UUID lotId = UUID.randomUUID();

        // 1 HARD_FAIL (+...0001) + 1 TRANSIENT_FAIL (+...0002)
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000001", "+255712000002"));

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
                "name", "DLX Retry Test Campaign",
                "body", "Test message",
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

        // Both messages should become FAILED after DLX routing (ladder TTL: 2+4+6=12s total + margin)
        // Await: both outbound messages are FAILED
        Awaitility.await("Both messages reach FAILED status via DLX ladder")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<OutboundMessage> messages = outboundMessageRepository.findByCampaignId(campaignId);
                    assertThat(messages).hasSize(2);
                    assertThat(messages).allMatch(m -> m.getStatus() == MessageStatus.FAILED);
                });

        // Two MessageRefundDue outbox entries must exist (one per FAILED message)
        // Each must have a unique eventId and carry the lotId
        Awaitility.await("Two MessageRefundDue outbox entries written")
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    var refundEntries = outboxRepository.findBySentFalse().stream()
                            .filter(e -> "MessageRefundDue".equals(e.getEventType()))
                            .toList();
                    assertThat(refundEntries)
                            .as("Expected 2 MessageRefundDue entries (one per failed message)")
                            .hasSize(2);

                    // Each entry carries the lotId in its payload
                    refundEntries.forEach(entry ->
                            assertThat(entry.getPayload()).contains(lotId.toString()));

                    // EventIds must be unique (T-04-15: dedup guard relies on distinct eventIds)
                    var eventIds = refundEntries.stream()
                            .map(e -> e.getEventId().toString())
                            .toList();
                    assertThat(eventIds).doesNotHaveDuplicates();
                });
    }
}
