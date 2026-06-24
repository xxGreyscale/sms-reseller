package com.smsreseller.messaging.senderid;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SenderIdRepository senderIdRepository;

    /**
     * List the sender-ID approval queue (ADMN-04).
     * GET /api/v1/internal/sender-ids?status=REQUESTED&page=0&size=50 → Spring Page of requests,
     * newest-first. Omitting status returns all statuses. Returns {content, totalElements, ...}.
     */
    @GetMapping
    public ResponseEntity<Page<SenderIdDto.SenderIdResponse>> list(
            @RequestParam(name = "status", required = false) SenderIdStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size);
        Page<SenderIdRequest> result = (status == null)
                ? senderIdRepository.findAllByOrderByCreatedAtDesc(pageable)
                : senderIdRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ResponseEntity.ok(result.map(SenderIdDto.SenderIdResponse::from));
    }

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
