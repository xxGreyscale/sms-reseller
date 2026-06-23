package com.smsreseller.messaging.analytics;

// Wave 0 RED placeholder — made GREEN by plan 05-02
// Requirement: ANLX-03 — operator-level delivery rates

import com.smsreseller.messaging.AbstractMessagingIntegrationTest;
import com.smsreseller.messaging.JwtTestHelper;
import com.smsreseller.messaging.campaign.Campaign;
import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignStatus;
import com.smsreseller.messaging.message.MessageStatus;
import com.smsreseller.messaging.message.OutboundMessage;
import com.smsreseller.messaging.message.OutboundMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED→GREEN: verifies GET /api/v1/analytics/operator-rates returns
 * delivery rates grouped by operator scoped to JWT subject.
 */
class OperatorRateAnalyticsIT extends AbstractMessagingIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @Autowired
    CampaignRepository campaignRepository;

    @Autowired
    OutboundMessageRepository outboundMessageRepository;

    @Test
    void operatorDeliveryRatesGroupedByProvider() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        campaignRepository.save(Campaign.builder()
                .id(campaignId)
                .userId(userId)
                .senderId("TEST")
                .name("Test Campaign").body("Hello")
                .status(CampaignStatus.COMPLETED)
                .build());

        // Vodacom +2557xx, Tigo +2557[1]x, Airtel +2557[89]x
        outboundMessageRepository.saveAll(List.of(
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255740000001").lotId(UUID.randomUUID())
                        .operator("Vodacom").status(MessageStatus.DELIVERED).build(),
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255740000002").lotId(UUID.randomUUID())
                        .operator("Vodacom").status(MessageStatus.FAILED).build(),
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255710000003").lotId(UUID.randomUUID())
                        .operator("Tigo").status(MessageStatus.DELIVERED).build()
        ));

        String token = jwtTestHelper.createToken(userId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/operator-rates",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("operator");
        assertThat(response.getBody()).contains("count");
        // Should see Vodacom and Tigo entries
        assertThat(response.getBody()).contains("Vodacom");
        assertThat(response.getBody()).contains("Tigo");
    }

    @Test
    void operatorRatesOnlyOwnData() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID campaignId1 = UUID.randomUUID();
        UUID campaignId2 = UUID.randomUUID();

        campaignRepository.saveAll(List.of(
                Campaign.builder().id(campaignId1).userId(user1).senderId("TEST")
                        .name("Campaign A").body("A").status(CampaignStatus.COMPLETED).build(),
                Campaign.builder().id(campaignId2).userId(user2).senderId("TEST")
                        .name("Campaign B").body("B").status(CampaignStatus.COMPLETED).build()
        ));

        outboundMessageRepository.saveAll(List.of(
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId1).userId(user1)
                        .phoneE164("+255740000011").lotId(UUID.randomUUID())
                        .operator("Vodacom").status(MessageStatus.DELIVERED).build(),
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId2).userId(user2)
                        .phoneE164("+255620000022").lotId(UUID.randomUUID())
                        .operator("Halotel").status(MessageStatus.DELIVERED).build()
        ));

        // user1 should only see Vodacom — not Halotel (user2's data)
        String token = jwtTestHelper.createToken(user1.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/operator-rates",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Vodacom");
        assertThat(response.getBody()).doesNotContain("Halotel");
    }

    @Test
    void unauthenticatedRequestReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/analytics/operator-rates", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
