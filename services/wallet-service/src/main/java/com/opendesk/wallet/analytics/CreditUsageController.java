package com.opendesk.wallet.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Analytics endpoint for ANLX-02: daily credit-usage spend trend.
 *
 * <p>JWT-scoped: userId is derived from the authenticated JWT subject — never from a query
 * parameter. This eliminates IDOR risk (T-05-10). The authenticated() matcher on
 * /api/v1/analytics/** is enforced by SecurityConfig.
 *
 * <p>Uses method-parameter injection of {@link JwtAuthenticationToken} rather than
 * SecurityContextHolder — preferred with virtual threads (Java 21, CLAUDE.md).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class CreditUsageController {

    private final CreditUsageService creditUsageService;

    /**
     * Returns daily credit consumption aggregates for the authenticated caller.
     *
     * <p>Only the caller's own transactions are returned — no cross-user data exposure.
     * Days with no consumption are absent from the response (gap-fill is the client's concern).
     *
     * @param auth injected JWT token — provides caller's userId via {@code getSubject()}
     * @return list of {@link CreditUsageDto}, newest-first, last 90 days
     */
    @GetMapping("/credit-usage")
    public ResponseEntity<List<CreditUsageDto>> getCreditUsage(JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        List<CreditUsageDto> usage = creditUsageService.getDailyUsage(userId);
        return ResponseEntity.ok(usage);
    }
}
