package com.opendesk.identity.verification;

/**
 * Thrown by {@link NidaVerificationService} when NIDA is temporarily unavailable
 * (timeout, network error, 5xx response).
 *
 * <p>This is a transient failure — the verification SHOULD be retried.
 * {@link VerificationOrchestrator} catches this and leaves the user in PENDING status.
 * {@link VerificationRetryJob} will re-dispatch the verification on its next tick (IDEN-08).
 *
 * <p>Not thrown for permanent failures (identity mismatch / invalid NIN) — those
 * return {@link NidaResult#REJECTED}.
 */
public class NidaTransientException extends RuntimeException {

    public NidaTransientException(String message) {
        super(message);
    }

    public NidaTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
