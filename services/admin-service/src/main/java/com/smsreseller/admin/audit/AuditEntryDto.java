package com.smsreseller.admin.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for the audit viewer API (ADMN-06).
 *
 * <p>Columns align with UI-SPEC §Audit Log:
 * Timestamp, Actor, Action (monospace), Target, Details (JSON string for expansion).
 */
public record AuditEntryDto(
        UUID id,
        Instant timestamp,
        String actor,
        String action,
        String target,
        String details
) {

    /** Factory from domain entity. */
    public static AuditEntryDto from(AuditEntry entry) {
        return new AuditEntryDto(
                entry.getId(),
                entry.getTimestamp(),
                entry.getActor(),
                entry.getAction(),
                entry.getTarget(),
                entry.getDetails()
        );
    }
}
