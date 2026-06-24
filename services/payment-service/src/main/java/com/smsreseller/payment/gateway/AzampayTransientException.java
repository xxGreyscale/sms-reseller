package com.smsreseller.payment.gateway;

/**
 * Thrown when an Azampay API call fails transiently (circuit breaker open, timeout, 5xx).
 *
 * <p>Used as the trigger for {@code @Retry} and {@code @CircuitBreaker} on
 * {@link AzampayPaymentGateway} methods (T-03-19).
 */
public class AzampayTransientException extends RuntimeException {

    public AzampayTransientException(String message) {
        super(message);
    }

    public AzampayTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
