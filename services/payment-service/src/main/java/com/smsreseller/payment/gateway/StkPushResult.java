package com.smsreseller.payment.gateway;

/**
 * Result of initiating an Azampay STK push.
 *
 * <p>A successful result means Azampay accepted the push and is prompting the customer.
 * The actual payment outcome is delivered asynchronously via callback or polling
 * ({@link PaymentGateway#queryTransactionStatus}).
 */
public final class StkPushResult {

    private final boolean success;
    private final String externalId;
    private final String message;

    private StkPushResult(boolean success, String externalId, String message) {
        this.success = success;
        this.externalId = externalId;
        this.message = message;
    }

    /**
     * Factory: Azampay accepted the STK push request.
     *
     * @param externalId Azampay reference (= payment UUID string for idempotency)
     */
    public static StkPushResult accepted(String externalId) {
        return new StkPushResult(true, externalId, "STK push accepted");
    }

    /**
     * Factory: Azampay rejected the STK push (e.g. invalid MSISDN, circuit open).
     */
    public static StkPushResult rejected(String message) {
        return new StkPushResult(false, null, message);
    }

    /** Whether the STK push was accepted by Azampay. */
    public boolean isSuccess() {
        return success;
    }

    /** Azampay idempotency reference (null if rejected). */
    public String getExternalId() {
        return externalId;
    }

    /** Human-readable message for logging. */
    public String getMessage() {
        return message;
    }
}
