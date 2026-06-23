package com.smsreseller.wallet.reservation;

/**
 * Thrown by {@link ReservationService} when the requested credit count exceeds the
 * user's available (non-expired, non-reserved) balance.
 *
 * <p>This is a runtime exception so that {@code @Transactional} rolls back the
 * reservation atomically — no partial state is committed.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException(String message) {
        super(message);
    }
}
