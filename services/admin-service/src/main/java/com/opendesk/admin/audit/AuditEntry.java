package com.opendesk.admin.audit;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log entry (ADMN-06, D-09).
 *
 * <p>This entity is NEVER updated or deleted — rows are only inserted.
 * The {@link AuditRepository} deliberately exposes no update or delete
 * methods for audit_entries (T-05-17: tamper prevention).
 *
 * <p>Columns align with UI-SPEC §Audit Log:
 * <ul>
 *   <li>timestamp — ISO instant (newest-first default sort)</li>
 *   <li>actor — admin email ("admin@opendesk.co") or {@code "system"} for event-driven rows</li>
 *   <li>action — monospace label e.g. {@code SENDER_ID_APPROVED}, {@code UserVerified}</li>
 *   <li>target — aggregate id or resource the action was applied to</li>
 *   <li>details — JSONB payload for the Details column (expandable in UI)</li>
 * </ul>
 */
@Entity
@Table(schema = "admin", name = "audit_entries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Server-set timestamp — never supplied by the caller (T-05-17). */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    /** Admin email or {@code "system"} for event-driven audit rows. */
    @Column(nullable = false)
    private String actor;

    /** Monospace action label e.g. {@code SENDER_ID_APPROVED}, {@code UserVerified}. */
    @Column(nullable = false)
    private String action;

    /** Aggregate id or resource identifier the action was applied to. */
    @Column
    private String target;

    /** Optional JSONB payload shown in the UI Details column. */
    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String details;
}
