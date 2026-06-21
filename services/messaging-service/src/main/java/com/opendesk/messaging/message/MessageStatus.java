package com.opendesk.messaging.message;

/**
 * Per-message delivery states.
 *
 * <p>State transitions:
 * <pre>
 * PENDING → SENT (SmsProvider returns ACCEPTED)
 * SENT → DELIVERED (delivery receipt received — stub: after dlr-delay-ms)
 * PENDING | SENT → FAILED (HARD_FAIL or delivery limit exhausted)
 * </pre>
 */
public enum MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}
