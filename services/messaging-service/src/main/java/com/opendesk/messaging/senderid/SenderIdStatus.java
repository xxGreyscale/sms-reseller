package com.opendesk.messaging.senderid;

/**
 * Sender ID request states (Pattern 7).
 *
 * <pre>
 * REQUESTED ──(admin approve)──► APPROVED
 *           ──(admin reject) ──► REJECTED
 * </pre>
 */
public enum SenderIdStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
