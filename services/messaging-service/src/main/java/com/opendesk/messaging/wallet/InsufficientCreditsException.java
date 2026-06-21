package com.opendesk.messaging.wallet;

/**
 * Thrown when wallet-service returns 409 (insufficient credits) during reservation.
 *
 * <p>Caught by CampaignService.executeSend() to abort the dispatch and leave the campaign
 * in DRAFT status. Surfaces to the caller as HTTP 402/409 (MESG-03 / T-04-10).
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException(String message) {
        super(message);
    }

    public InsufficientCreditsException(String message, Throwable cause) {
        super(message, cause);
    }
}
