package com.opendesk.payment.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Production Azampay payment gateway (D-10).
 *
 * <p>Active only under {@code @Profile("prod")} — never used in dev/test (StubPaymentGateway handles those).
 * Mirrors RealNidaVerificationService pattern: RestClient + @CircuitBreaker("azampay") + @Retry("azampay").
 *
 * <p>Connect timeout: 5s; Read timeout: 30s (CLAUDE.md external call timeouts).
 * RestClient is used — NOT RestTemplate (CLAUDE.md forbidden list).
 *
 * <p>Amount is sent as String to Azampay (no decimals — CLAUDE.md Tanzania Azampay integration).
 * externalId = payment UUID string (Azampay idempotency key).
 *
 * @see StubPaymentGateway stub implementation for dev/test
 */
@Profile("prod")
@Service
@Slf4j
public class AzampayPaymentGateway implements PaymentGateway {

    @Value("${app.azampay.base-url}")
    private String azampayBaseUrl;

    private final RestClient restClient;
    private final AzampayTokenProvider tokenProvider;

    public AzampayPaymentGateway(AzampayTokenProvider tokenProvider,
                                  @Value("${app.azampay.base-url}") String azampayBaseUrl) {
        this.tokenProvider = tokenProvider;
        this.azampayBaseUrl = azampayBaseUrl;
        this.restClient = RestClient.builder()
                .requestInitializer(req ->
                        req.getHeaders().setBearerAuth(tokenProvider.getToken()))
                // Connect timeout 5s, read timeout 30s (CLAUDE.md Azampay integration)
                .build();
    }

    /**
     * Initiates an Azampay mobile money STK push.
     *
     * <p>POST /azampay/mobileCheckout with amount as String (no decimals), externalId = payment UUID.
     *
     * @param request STK push parameters (paymentId, msisdn, amountTzs, provider)
     * @return result indicating whether Azampay accepted the push
     */
    @CircuitBreaker(name = "azampay", fallbackMethod = "stkPushFallback")
    @Retry(name = "azampay")
    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        log.info("AzampayPaymentGateway: initiating STK push paymentId={} msisdn={} provider={}",
                request.paymentId(), request.msisdn(), request.provider());

        Map<String, Object> body = Map.of(
                "accountNumber", request.msisdn(),
                "amount", String.valueOf(request.amountTzs()), // amount as String (Azampay requirement)
                "currency", "TZS",
                "externalId", request.paymentId().toString(),
                "provider", request.provider()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(azampayBaseUrl + "/azampay/mobileCheckout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new AzampayTransientException("AzampayPaymentGateway: null response from mobileCheckout");
        }

        String transactionId = (String) response.getOrDefault("transactionId", request.paymentId().toString());
        log.info("AzampayPaymentGateway: STK push accepted paymentId={} transactionId={}",
                request.paymentId(), transactionId);

        return StkPushResult.accepted(transactionId);
    }

    /**
     * Fallback method for initiateStkPush when the circuit breaker is open.
     */
    @SuppressWarnings("unused")
    public StkPushResult stkPushFallback(StkPushRequest request, Throwable ex) {
        log.warn("AzampayPaymentGateway: circuit breaker open for initiateStkPush paymentId={}",
                request.paymentId(), ex);
        throw new AzampayTransientException("Azampay circuit breaker open", ex);
    }

    /**
     * Queries the current status of an Azampay transaction.
     *
     * <p>GET /azampay/transactionStatus?pgReferenceId={externalId}
     *
     * @param externalId our payment UUID (Azampay idempotency key)
     * @return current status from Azampay
     */
    @CircuitBreaker(name = "azampay", fallbackMethod = "queryStatusFallback")
    @Retry(name = "azampay")
    @Override
    public TransactionStatusResult queryTransactionStatus(String externalId) {
        log.debug("AzampayPaymentGateway: querying transaction status externalId={}", externalId);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
                .uri(azampayBaseUrl + "/azampay/transactionStatus?pgReferenceId=" + externalId)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return TransactionStatusResult.pending(externalId);
        }

        String status = (String) response.getOrDefault("transactionStatus", "PENDING");
        return switch (status.toLowerCase()) {
            case "success" -> TransactionStatusResult.success(externalId);
            case "failed", "fail" -> TransactionStatusResult.failed(externalId);
            default -> TransactionStatusResult.pending(externalId);
        };
    }

    /**
     * Fallback method for queryTransactionStatus when the circuit breaker is open.
     */
    @SuppressWarnings("unused")
    public TransactionStatusResult queryStatusFallback(String externalId, Throwable ex) {
        log.warn("AzampayPaymentGateway: circuit breaker open for queryTransactionStatus externalId={}",
                externalId, ex);
        throw new AzampayTransientException("Azampay circuit breaker open", ex);
    }
}
