package com.opendesk.admin.audit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin mutation recording seam (D-09a).
 *
 * <p>Admin-web POSTs here when the operator performs a platform mutation (e.g. approving
 * a sender ID, issuing a manual refund). This endpoint writes a mutation-source audit row
 * into the append-only audit log.
 *
 * <p>Protected by SecurityConfig: {@code /api/v1/admin/**} requires {@code ROLE_ADMIN}.
 *
 * <p>This is the D-09a "mutation seam" that admin-web (05-09) will call after performing
 * a privileged operation, letting the audit-service record exactly what happened without
 * each owning service writing into a shared audit table (D-14).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AdminMutationController {

    private final AuditService auditService;

    /**
     * Records an admin mutation in the audit log (D-09a).
     *
     * @param request mutation details (actor, action, target, optional details JSON)
     * @return 204 No Content on success
     */
    @PostMapping("/record")
    public ResponseEntity<Void> record(@Valid @RequestBody MutationRecordRequest request) {
        auditService.recordMutation(
                request.actor(),
                request.action(),
                request.target(),
                request.details()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * Request body for POST /api/v1/admin/audit/record.
     *
     * @param actor   admin email performing the mutation (required)
     * @param action  monospace action label e.g. {@code SENDER_ID_APPROVED} (required)
     * @param target  aggregate id or resource the action was applied to (optional)
     * @param details optional JSON payload for the Details column in the audit viewer
     */
    public record MutationRecordRequest(
            @NotBlank String actor,
            @NotBlank String action,
            String target,
            String details
    ) {}
}
