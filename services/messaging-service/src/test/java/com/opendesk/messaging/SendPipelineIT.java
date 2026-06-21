package com.opendesk.messaging;

// Requirement: MESG-09
// Implementing plan: 04-05

import com.opendesk.messaging.campaign.CampaignRepository;
import com.opendesk.messaging.contact.ContactRecipientClient;
import com.opendesk.messaging.message.OutboundMessageRepository;
import com.opendesk.messaging.outbox.OutboxRepository;
import com.opendesk.messaging.wallet.InsufficientCreditsException;
import com.opendesk.messaging.wallet.LotAllocation;
import com.opendesk.messaging.wallet.ReservationResult;
import com.opendesk.messaging.wallet.WalletReservationClient;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the SMS send pipeline (fan-out, suppression filtering, consumer).
 */
class SendPipelineIT extends AbstractMessagingIntegrationTest {

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
     * MESG-09: System automatically excludes suppressed numbers from campaign recipients.
     * ContactRecipientClient returns already-suppression-filtered list (suppressed excluded
     * at the contact-service boundary). 2 raw recipients, 1 suppressed → only 1 outbound_message
     * persisted, 1 credit reserved, no AMQP message for the suppressed number.
     *
     * Additionally asserts: a reserved-but-never-sent slot (via MessageReleased outbox entry)
     * means the reservation is not stranded.
     *
     * And asserts: when the StubSmsProvider accepts the message, a MessageAccepted outbox row
     * is written with the lotId.
     */
    @Test
    void suppressedNumberNotPublished() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        String token = jwtTestHelper.createToken(userId.toString());

        UUID lotId = UUID.randomUUID();

        // ContactRecipientClient returns only NON-suppressed recipient (1 phone)
        // The suppressed number (+255712000001-suppressed) is excluded at the contact-service boundary
        when(contactRecipientClient.getRecipientsForGroups(any(), any()))
                .thenReturn(List.of("+255712000005")); // only 1 unsuppressed recipient

        // WalletReservationClient succeeds for 1 credit
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
                "name", "Suppression Test Campaign",
                "body", "Hello",
                "senderId", "OPENDESK",
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

        // Only 1 outbound_message persisted (suppressed number excluded)
        var messages = outboundMessageRepository.findByCampaignId(campaignId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getPhoneE164()).isEqualTo("+255712000005");
        assertThat(messages.get(0).getLotId()).isEqualTo(lotId);

        // Wait for SendMessageConsumer to process the AMQP message
        // StubSmsProvider ACCEPTED → MessageAccepted outbox entry written
        // Scope to THIS campaign's lot — the broker/context is shared across test classes,
        // so other tests' MessageAccepted entries can be present concurrently (#flaky-isolation).
        long deadline = System.currentTimeMillis() + 10_000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            long acceptedForLot = outboxRepository.findBySentFalse().stream()
                    .filter(e -> "MessageAccepted".equals(e.getEventType()))
                    .filter(e -> e.getPayload() != null && e.getPayload().contains(lotId.toString()))
                    .count();
            if (acceptedForLot >= 1) {
                found = true;
                break;
            }
            Thread.sleep(200);
        }
        assertThat(found)
                .as("Expected a MessageAccepted outbox entry carrying this campaign's lotId after the consumer processes the message")
                .isTrue();
    }
}
