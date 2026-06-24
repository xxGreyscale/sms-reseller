package com.smsreseller.admin.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Audit log viewer API (ADMN-06).
 *
 * <p>Protected by SecurityConfig: {@code /api/v1/admin/**} requires {@code ROLE_ADMIN}.
 *
 * <p>UI-SPEC §Audit Log filters: date range (from/to as ISO-8601 instants) and actor substring.
 * Results are paginated, newest-first.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * Returns a filterable, paged list of audit log entries (ADMN-06).
     *
     * @param from  optional lower bound (ISO-8601 instant)
     * @param to    optional upper bound (ISO-8601 instant)
     * @param actor optional actor substring filter (case-insensitive)
     * @param page  zero-based page index (default 0)
     * @param size  page size (default 20)
     */
    @GetMapping
    public ResponseEntity<Page<AuditEntryDto>> getAuditLog(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(auditService.search(from, to, actor, page, size));
    }
}
