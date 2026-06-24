package com.smsreseller.admin.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for the admin audit log (ADMN-06, D-09).
 *
 * <p>Provides two operations:
 * <ol>
 *   <li>{@link #recordMutation} — append an audit row from an admin mutation (D-09a)</li>
 *   <li>{@link #search} — paged, filterable read for the audit viewer</li>
 * </ol>
 *
 * <p>The audit log is append-only: no method here performs UPDATE or DELETE on audit_entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditRepository auditRepository;

    /**
     * Appends a single audit row for an admin mutation (D-09a).
     *
     * <p>Timestamp is server-set ({@code @CreatedDate}); the caller only supplies
     * the semantic fields. This method is {@code @Transactional} so the caller's
     * surrounding transaction includes the audit write (atomicity).
     *
     * @param actor   admin email (or {@code "system"} for event-driven writes)
     * @param action  monospace action label e.g. {@code SENDER_ID_APPROVED}
     * @param target  aggregate id or resource the action was applied to (nullable)
     * @param details optional JSON payload for the Details column (nullable)
     */
    @Transactional
    public void recordMutation(String actor, String action, String target, String details) {
        AuditEntry entry = AuditEntry.builder()
                .actor(actor)
                .action(action)
                .target(target)
                .details(details)
                .build();
        auditRepository.save(entry);
        log.info("Audit row written: actor={} action={} target={}", actor, action, target);
    }

    /**
     * Paged, filterable read for the ADMN-06 viewer API.
     *
     * <p>All filter params are optional (null means no filter). Results are newest-first.
     *
     * @param from   earliest timestamp (inclusive) — null means no lower bound
     * @param to     latest timestamp (inclusive) — null means no upper bound
     * @param actor  case-insensitive actor substring — null means no actor filter
     * @param page   zero-based page index
     * @param size   page size
     * @return paged audit entries mapped to DTOs
     */
    @Transactional(readOnly = true)
    public Page<AuditEntryDto> search(Instant from, Instant to, String actor, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return auditRepository.search(from, to, actor, pageRequest).map(AuditEntryDto::from);
    }
}
