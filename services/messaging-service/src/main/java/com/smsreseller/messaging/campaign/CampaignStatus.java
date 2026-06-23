package com.smsreseller.messaging.campaign;

/**
 * Campaign lifecycle states.
 *
 * <p>State machine:
 * <pre>
 * DRAFT ──(user sends / schedules)──► SCHEDULED
 *       ──(user sends immediately) ──► QUEUED
 * SCHEDULED ──(dispatcher at due time)──► QUEUED
 * QUEUED ──(first AMQP message published)──► SENDING
 * SENDING ──(all messages processed)──► COMPLETED
 * SCHEDULED | QUEUED ──(user cancels)──► CANCELLED
 * </pre>
 */
public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    QUEUED,
    SENDING,
    COMPLETED,
    CANCELLED
}
