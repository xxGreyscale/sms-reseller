package com.opendesk.wallet.refund;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal refund endpoint — callable by Phase 4 campaign dispatch.
 *
 * <p>{@code POST /api/v1/wallet/refunds} accepts a {@link RefundRequest} and drives the
 * idempotent {@link RefundService#refund} path. Authentication is required (JWT) — this is
 * an internal service-to-service call authenticated via a service JWT.
 *
 * <p>Callers supply an idempotencyKey to make the call safe to retry across service restarts
 * without risk of double-crediting the user (D-07, PYMT-08).
 */
@RestController
@RequestMapping("/api/v1/wallet/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * Credits back the specified number of SMS credits to the user's wallet.
     *
     * <p>Idempotent: re-submitting the same {@code idempotencyKey} is a no-op.
     * Returns 200 OK regardless of whether the refund was newly applied or was a duplicate.
     *
     * @param request refund request with userId, credits, referenceId, idempotencyKey
     * @return 200 OK (new or duplicate)
     */
    @PostMapping
    public ResponseEntity<Void> refund(@Valid @RequestBody RefundRequest request) {
        refundService.refund(
                request.userId(),
                request.credits(),
                request.referenceId(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok().build();
    }
}
