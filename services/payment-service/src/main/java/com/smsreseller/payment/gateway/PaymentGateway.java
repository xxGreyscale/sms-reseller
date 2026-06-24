package com.smsreseller.payment.gateway;

/**
 * Mock-first payment gateway contract (D-10).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link StubPaymentGateway} — {@code @Profile("stub")}, configurable outcomes for
 *       dev/test. Active in local dev and CI. Full purchase flow (including countdown + EXPIRED)
 *       is demoable before Azampay credentials exist.</li>
 *   <li>{@code AzampayPaymentGateway} — {@code @Profile("prod")}, {@code RestClient} +
 *       Resilience4j circuit breaker. Wired when the Azampay merchant account is onboarded
 *       (Plan 06). Not implemented in this plan.</li>
 * </ul>
 *
 * <p>Pattern mirrors {@code NidaVerificationService} from Phase 2 (D-10, 03-PATTERNS.md §2).
 */
public interface PaymentGateway {

    /**
     * Initiates an Azampay STK push — prompts the customer to approve on their phone.
     *
     * @param request payment details (paymentId, msisdn, amountTzs, provider)
     * @return result indicating whether Azampay accepted the push request
     */
    StkPushResult initiateStkPush(StkPushRequest request);

    /**
     * Queries the current status of a payment from Azampay.
     * Used by the reconciliation job to handle late callbacks and stale PENDING/EXPIRED records.
     *
     * @param externalId Azampay idempotency key (= payment UUID string)
     * @return current status from Azampay: success, failed, or pending
     */
    TransactionStatusResult queryTransactionStatus(String externalId);
}
