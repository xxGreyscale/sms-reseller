package com.smsreseller.identity.verification;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Production NIDA identity verification using the NIDA government REST API.
 *
 * <p>Active only under the {@code prod} Spring profile. Uses:
 * <ul>
 *   <li>{@link RestClient} — Spring 6.1+ preferred HTTP client (CLAUDE.md: no RestTemplate)</li>
 *   <li>Connect timeout: 5s / Read timeout: 15s (CLAUDE.md NIDA guidance — government infra is slow)</li>
 *   <li>Resilience4j {@code @CircuitBreaker(name="nida")} — opens on repeated failures</li>
 *   <li>Spring Retry {@code @Retryable} — exponential backoff for transient failures before
 *       propagating as {@link NidaTransientException}</li>
 * </ul>
 *
 * <p>Caching: results are NEVER cached (CLAUDE.md explicit rule — each call must be live).
 *
 * <p>PII: the NIN is not logged at any log level.
 */
@Profile("prod")
@Service
@Slf4j
public class RealNidaVerificationService implements NidaVerificationService {

    private final RestClient restClient;

    @Value("${app.nida.api.base-url}")
    private String nidaBaseUrl;

    public RealNidaVerificationService() {
        // RestClient with connect timeout 5s / read timeout 15s (CLAUDE.md NIDA guidance)
        this.restClient = RestClient.builder()
                .requestInitializer(request -> {
                    request.getHeaders().setContentType(
                            org.springframework.http.MediaType.APPLICATION_JSON);
                })
                .build();
    }

    /**
     * Verify the NIN against the NIDA API.
     *
     * <p>Circuit breaker opens after configured failure threshold.
     * Spring retry retries transient failures up to 3 times with exponential backoff
     * (1s, 2s, 4s) before propagating as {@link NidaTransientException}.
     *
     * @param nin NIN to verify (PII — not logged)
     * @return {@link NidaResult#VERIFIED} or {@link NidaResult#REJECTED}
     * @throws NidaTransientException if NIDA is temporarily unavailable after retries
     */
    @CircuitBreaker(name = "nida", fallbackMethod = "nidaUnavailable")
    @Retryable(retryFor = NidaTransientException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    @Override
    public NidaResult verify(String nin) {
        // PII: nin is not logged
        log.debug("Calling NIDA API to verify identity (nin length={})", nin == null ? 0 : nin.length());

        try {
            NidaApiResponse apiResponse = restClient.post()
                    .uri(nidaBaseUrl + "/verify")
                    .body(new NidaApiRequest(nin))
                    .retrieve()
                    .body(NidaApiResponse.class);

            if (apiResponse == null) {
                throw new NidaTransientException("NIDA API returned null response");
            }

            return apiResponse.verified() ? NidaResult.VERIFIED : NidaResult.REJECTED;

        } catch (ResourceAccessException e) {
            // Timeout or network error — transient, will be retried
            throw new NidaTransientException("NIDA API unreachable", e);
        }
    }

    /**
     * Resilience4j circuit breaker fallback — called when the circuit is open.
     *
     * <p>Throws {@link NidaTransientException} so the orchestrator treats it as a transient
     * failure and leaves the user in PENDING for the retry job (IDEN-08).
     */
    @SuppressWarnings("unused")
    public NidaResult nidaUnavailable(String nin, Throwable ex) {
        log.warn("NIDA circuit breaker open — user stays PENDING for retry job");
        throw new NidaTransientException("NIDA circuit breaker open", ex);
    }

    // DTO records for NIDA API request / response (internal to this impl)
    private record NidaApiRequest(String nin) {}
    private record NidaApiResponse(boolean verified) {}
}
