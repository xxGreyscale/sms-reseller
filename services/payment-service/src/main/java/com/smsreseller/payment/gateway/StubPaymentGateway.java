package com.smsreseller.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of {@link PaymentGateway} for dev/test environments (D-10).
 *
 * <p>Active when the {@code stub} Spring profile is set (local dev, CI, staging).
 * Mirrors the magic-suffix pattern from {@code StubNidaVerificationService} (Phase 2, D-05):
 *
 * <ul>
 *   <li>externalId ending {@code ...0001} → FAILURE on {@link #queryTransactionStatus}</li>
 *   <li>externalId ending {@code ...0002} → TIMEOUT (always returns PENDING — no auto-success)</li>
 *   <li>all others → SUCCESS after configurable delay ({@code app.payment.stub.delay-ms})</li>
 * </ul>
 *
 * <p>This allows manual testing of all payment outcomes without Azampay credentials.
 * The full purchase flow including 2-minute countdown + EXPIRED state (D-06) is demoable.
 *
 * <p>AzampayPaymentGateway ({@code @Profile("prod")}) will replace this in production.
 * It is not implemented in this plan — Plan 06 owns the prod gateway.
 */
@Profile("stub")
@Service
@Slf4j
public class StubPaymentGateway implements PaymentGateway {

    private static final String FAILURE_SUFFIX = "0001";
    private static final String TIMEOUT_SUFFIX = "0002";

    @Value("${app.payment.stub.default-outcome:SUCCESS}")
    private String defaultOutcome;

    @Value("${app.payment.stub.delay-ms:500}")
    private long delayMs;

    /**
     * Initiates a stub STK push — always returns "accepted" immediately.
     * The actual outcome is determined on {@link #queryTransactionStatus} based on externalId suffix.
     */
    @Override
    public StkPushResult initiateStkPush(StkPushRequest request) {
        String externalId = request.paymentId().toString();
        String outcome = resolveOutcome(externalId);
        log.debug("StubPaymentGateway: initiateStkPush paymentId={} resolvedOutcome={}",
                externalId, outcome);
        // Always return accepted immediately — outcome delivered on queryTransactionStatus
        return StkPushResult.accepted(externalId);
    }

    /**
     * Resolves transaction status based on the externalId suffix (magic pattern).
     * In real Azampay, this would be an HTTP call to the transaction status endpoint.
     */
    @Override
    public TransactionStatusResult queryTransactionStatus(String externalId) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String outcome = resolveOutcome(externalId);
        log.debug("StubPaymentGateway: queryTransactionStatus externalId={} outcome={}", externalId, outcome);
        return switch (outcome) {
            case "FAILURE" -> TransactionStatusResult.failed(externalId);
            case "TIMEOUT" -> TransactionStatusResult.pending(externalId);
            default -> TransactionStatusResult.success(externalId);
        };
    }

    /**
     * Determines outcome from externalId suffix or falls back to configured default.
     *
     * @param externalId payment UUID string (Azampay idempotency key)
     * @return "FAILURE" | "TIMEOUT" | "SUCCESS"
     */
    private String resolveOutcome(String externalId) {
        if (externalId != null) {
            if (externalId.endsWith(FAILURE_SUFFIX)) {
                return "FAILURE";
            }
            if (externalId.endsWith(TIMEOUT_SUFFIX)) {
                return "TIMEOUT";
            }
        }
        return defaultOutcome;
    }
}
