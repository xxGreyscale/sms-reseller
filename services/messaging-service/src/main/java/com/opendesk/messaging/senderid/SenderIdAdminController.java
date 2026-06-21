package com.opendesk.messaging.senderid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only sender-ID approve/reject endpoints (SNDR-03, T-04-17).
 *
 * <p>Secured by SecurityConfig: /api/v1/internal/** requires hasRole("ADMIN").
 * Non-admin JWTs receive 403 before reaching this controller.
 */
@RestController
@RequestMapping("/api/v1/internal/sender-ids")
@RequiredArgsConstructor
public class SenderIdAdminController {

    private final SenderIdService senderIdService;

    /**
     * Approve a sender-ID request (SNDR-03).
     * POST /api/v1/internal/sender-ids/{id}/approve → 200 OK with updated request.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id) {
        try {
            SenderIdRequest req = senderIdService.approve(id);
            return ResponseEntity.ok(SenderIdDto.SenderIdResponse.from(req));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(409).body(Map.of("error", "invalid_state", "message", e.getMessage()));
        }
    }

    /**
     * Reject a sender-ID request (SNDR-03).
     * POST /api/v1/internal/sender-ids/{id}/reject → 200 OK with updated request.
     * Body: {"reason": "..."} (optional).
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) SenderIdDto.RejectRequest body) {
        String reason = body != null ? body.reason() : null;
        try {
            SenderIdRequest req = senderIdService.reject(id, reason);
            return ResponseEntity.ok(SenderIdDto.SenderIdResponse.from(req));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(409).body(Map.of("error", "invalid_state", "message", e.getMessage()));
        }
    }
}
