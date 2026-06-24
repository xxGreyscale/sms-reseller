package com.smsreseller.messaging.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous REST client for the wallet-service reservation endpoint.
 *
 * <p>D-03: credit reservation is the SOLE synchronous call in the dispatch request path.
 * No retry is applied — a 409 means insufficient credits, not a transient error. Circuit breaker
 * (name="wallet") is applied via Resilience4j to prevent cascade failures when wallet is down.
 *
 * <p>Analog: AzampayPaymentGateway (04-PATTERNS.md §RestClient Sync HTTP Call).
 *
 * <p>T-04-10 (Tampering): campaign can only become QUEUED AFTER this call returns successfully.
 * On InsufficientCreditsException, the caller (CampaignService) aborts the dispatch.
 */
@Component
@Slf4j
public class WalletReservationClient {

    private final RestClient restClient;

    public WalletReservationClient(
            @Value("${app.wallet.base-url:http://wallet-service}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Reserve {@code count} credits for a campaign.
     *
     * @param userId      the campaign owner
     * @param count       number of credits to reserve (recipient count after suppression filter)
     * @param campaignId  idempotency key — wallet upserts on campaignId so double-call is safe
     * @return ReservationResult with per-lot allocations for D-13 lot zip
     * @throws InsufficientCreditsException when wallet returns 409
     */
    @CircuitBreaker(name = "wallet", fallbackMethod = "reserveFallback")
    public ReservationResult reserve(UUID userId, int count, UUID campaignId) {
        log.info("Reserving {} credits for campaign={} userId={}", count, campaignId, userId);

        Map<String, Object> requestBody = Map.of(
                "userId", userId.toString(),
                "count", count,
                "campaignId", campaignId.toString()
        );

        try {
            WalletReservationResponse response = restClient.post()
                    .uri("/api/v1/wallet/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (req, res) -> {
                        throw new InsufficientCreditsException(
                                "Insufficient credits for campaign=" + campaignId);
                    })
                    .body(WalletReservationResponse.class);

            if (response == null) {
                throw new IllegalStateException("Null response from wallet reservation for campaign=" + campaignId);
            }

            log.info("Reserved {} credits for campaign={} across {} lots",
                    response.reservedCount(), campaignId, response.allocations().size());
            return new ReservationResult(
                    response.allocations().stream().map(a -> a.lotId()).toList(),
                    response.reservedCount(),
                    response.allocations()
            );

        } catch (InsufficientCreditsException e) {
            throw e; // re-throw — not a fallback-worthy error
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new InsufficientCreditsException("Insufficient credits for campaign=" + campaignId, e);
            }
            throw e;
        }
    }

    /**
     * Circuit breaker fallback — wallet is down. Treat as transient failure.
     */
    public ReservationResult reserveFallback(UUID userId, int count, UUID campaignId, Throwable ex) {
        log.warn("Wallet reservation circuit breaker open for campaign={}: {}", campaignId, ex.getMessage());
        throw new InsufficientCreditsException("Wallet service unavailable", ex);
    }

    // ── Internal DTO (wallet response) ───────────────────────────────────────

    private record WalletReservationResponse(int reservedCount, List<LotAllocation> allocations) {}
}
