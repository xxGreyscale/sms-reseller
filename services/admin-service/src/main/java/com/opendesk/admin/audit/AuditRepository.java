package com.opendesk.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only repository for {@link AuditEntry}.
 *
 * <p>Intentionally exposes NO update or delete methods for audit_entries (T-05-17).
 * {@code JpaRepository.deleteAll()} is available for test teardown only and is NOT
 * called in any production path. The production audit log is written-once, read-many.
 *
 * <p>The {@code search} query supports the UI-SPEC §Audit Log filters:
 * <ul>
 *   <li>Date range: {@code from} / {@code to} (nullable — both optional)</li>
 *   <li>Actor filter: partial case-insensitive match (nullable — optional)</li>
 * </ul>
 * Results are ordered newest-first (timestamp DESC).
 */
public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {

    /**
     * Filterable paged query for the audit viewer (ADMN-06).
     *
     * @param from   earliest timestamp (inclusive) — null means no lower bound
     * @param to     latest timestamp (inclusive) — null means no upper bound
     * @param actor  case-insensitive actor substring — null means no actor filter
     * @param pageable pagination + sort (caller should supply newest-first)
     */
    @Query(
        value = """
            SELECT id, action, actor, details, target, timestamp
            FROM admin.audit_entries
            WHERE (CAST(:from AS timestamptz) IS NULL OR timestamp >= CAST(:from AS timestamptz))
              AND (CAST(:to   AS timestamptz) IS NULL OR timestamp <= CAST(:to   AS timestamptz))
              AND (CAST(:actor AS text) IS NULL OR lower(actor) LIKE lower('%' || CAST(:actor AS text) || '%'))
            ORDER BY timestamp DESC
            """,
        countQuery = """
            SELECT COUNT(*) FROM admin.audit_entries
            WHERE (CAST(:from AS timestamptz) IS NULL OR timestamp >= CAST(:from AS timestamptz))
              AND (CAST(:to   AS timestamptz) IS NULL OR timestamp <= CAST(:to   AS timestamptz))
              AND (CAST(:actor AS text) IS NULL OR lower(actor) LIKE lower('%' || CAST(:actor AS text) || '%'))
            """,
        nativeQuery = true
    )
    Page<AuditEntry> search(
            @Param("from")  Instant from,
            @Param("to")    Instant to,
            @Param("actor") String actor,
            Pageable pageable
    );

    // ── No update or delete methods exposed for audit_entries (append-only, T-05-17) ──
}
