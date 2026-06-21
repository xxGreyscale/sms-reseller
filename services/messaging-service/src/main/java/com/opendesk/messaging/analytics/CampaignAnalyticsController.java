package com.opendesk.messaging.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Analytics endpoints for messaging-service — ANLX-01 and ANLX-03.
 *
 * <p>Security: All endpoints are JWT-authenticated (SecurityConfig maps /api/v1/analytics/**
 * to authenticated). userId is always derived from {@code auth.getToken().getSubject()} to
 * prevent IDOR attacks (T-05-02). No SecurityContextHolder — virtual-thread safe (method injection).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class CampaignAnalyticsController {

    private final CampaignAnalyticsService analyticsService;

    /**
     * ANLX-01: Campaign delivery stats — total, delivered, failed.
     *
     * <p>Returns 404 if the campaign does not exist or is not owned by the JWT subject.
     */
    @GetMapping("/campaigns/{id}/stats")
    public ResponseEntity<CampaignStatsDto> getCampaignStats(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        CampaignStatsDto stats = analyticsService.getStats(userId, id);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * ANLX-03: Operator-level delivery rates grouped by (operator, status).
     * Scoped to JWT subject — user sees only their own messages.
     */
    @GetMapping("/operator-rates")
    public ResponseEntity<List<OperatorRateDto>> getOperatorRates(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(analyticsService.getOperatorRates(userId));
    }
}
