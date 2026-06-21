package com.opendesk.notification.consumer;

import java.util.UUID;

/**
 * Local event record definitions mirroring upstream service payloads.
 *
 * <p>Service-boundary rule: notification-service MUST NOT import classes from other services.
 * These records duplicate the shape of upstream events. Field names match the JSON keys
 * emitted by the source service (Jackson maps by field name by default).
 */
final class Events {

    private Events() {}

    /** identity.events / identity.UserVerified — NOTF-01 */
    record UserVerifiedEvent(String eventId, String userId, Integer freeCredits) {}

    /** payment.events / payment.PaymentConfirmed — NOTF-02 */
    record PaymentConfirmedEvent(String eventId, String userId, Long amountTzs, Integer smsCredits) {}

    /** wallet.events / wallet.LowCreditAlert — NOTF-03 */
    record LowCreditAlertEvent(String eventId, String userId, Integer availableCredits) {}

    /** wallet.events / wallet.ExpiryWarning — NOTF-04 */
    record ExpiryWarningEvent(String eventId, String userId, Integer remainingCredits, String expiresAt) {}

    /**
     * messaging.events / messaging.CampaignCompleted — NOTF-05
     * Payload contract from 05-02: {eventId, campaignId, userId, totalCount, deliveredCount, failedCount}.
     */
    record CampaignCompletedEvent(String eventId, String campaignId, String userId,
                                  Integer totalCount, Integer deliveredCount, Integer failedCount) {}

    /** messaging.events / messaging.SenderIdDecided — NOTF-06 */
    record SenderIdDecidedEvent(String eventId, String userId, String senderId, String decision) {}
}
