package com.smsreseller.messaging.analytics;

// Wave 0 RED placeholder — made GREEN by plan 05-02
// Requirement: ANLX-01 — campaign delivery statistics per campaign

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
 * RED→GREEN: verifies GET /api/v1/analytics/campaigns/{id}/stats returns
 * delivery statistics scoped to JWT subject (no IDOR).
 */
class CampaignAnalyticsIT extends AbstractMessagingIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JwtTestHelper jwtTestHelper;

    @Autowired
    CampaignRepository campaignRepository;

    @Autowired
    OutboundMessageRepository outboundMessageRepository;

    @Test
    void campaignDeliveryStatsReturnedForOwner() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        campaignRepository.save(Campaign.builder()
                .id(campaignId)
                .userId(userId)
                .senderId("TEST")
                .name("Test Campaign").body("Hello")
                .status(CampaignStatus.COMPLETED)
                .build());

        outboundMessageRepository.saveAll(List.of(
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255740000001").lotId(UUID.randomUUID()).status(MessageStatus.DELIVERED).build(),
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255710000002").lotId(UUID.randomUUID()).status(MessageStatus.DELIVERED).build(),
                OutboundMessage.builder().id(UUID.randomUUID()).campaignId(campaignId).userId(userId)
                        .phoneE164("+255780000003").lotId(UUID.randomUUID()).status(MessageStatus.FAILED).build()
        ));

        String token = jwtTestHelper.createToken(userId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/campaigns/" + campaignId + "/stats",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalCount");
        assertThat(response.getBody()).contains("deliveredCount");
        assertThat(response.getBody()).contains("failedCount");
        // 2 delivered, 1 failed, total 3
        assertThat(response.getBody()).contains("\"totalCount\":3");
        assertThat(response.getBody()).contains("\"deliveredCount\":2");
        assertThat(response.getBody()).contains("\"failedCount\":1");
    }

    @Test
    void anotherUsersCampaignReturns404() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        campaignRepository.save(Campaign.builder()
                .id(campaignId)
                .userId(owner)
                .senderId("TEST")
                .name("Private Campaign").body("Private")
                .status(CampaignStatus.COMPLETED)
                .build());

        String attackerToken = jwtTestHelper.createToken(attacker.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(attackerToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/campaigns/" + campaignId + "/stats",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequestReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/analytics/campaigns/" + UUID.randomUUID() + "/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
