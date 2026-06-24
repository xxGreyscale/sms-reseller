package com.smsreseller.notification.notification;

/**
 * Type of in-app notification — maps 1:1 to the 6 upstream events (NOTF-01..06).
 */
public enum NotificationType {
    NIDA_VERIFIED,        // NOTF-01: identity verified
    PAYMENT_CONFIRMED,    // NOTF-02: payment received
    LOW_CREDIT,           // NOTF-03: wallet low credit alert
    EXPIRY_WARNING,       // NOTF-04: 7-day credit expiry warning
    CAMPAIGN_COMPLETED,   // NOTF-05: campaign completed
    SENDER_ID_DECIDED     // NOTF-06: sender ID approval/rejection
}
