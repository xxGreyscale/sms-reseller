package com.smsreseller.payment.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user attempts to initiate a second payment while one is already PENDING (D-05).
 *
 * <p>Maps to HTTP 409 Conflict. The partial unique index {@code uq_payments_user_pending} is
 * the DB-level backstop; this exception provides the application-layer signal.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PendingPaymentExistsException extends RuntimeException {

    public PendingPaymentExistsException(String userId) {
        super("User " + userId + " already has a PENDING payment. Cancel or wait for it to expire.");
    }
}
