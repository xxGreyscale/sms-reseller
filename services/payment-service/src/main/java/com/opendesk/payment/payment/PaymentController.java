package com.opendesk.payment.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Payment REST controller.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/payments — initiate STK push purchase (PYMT-02)</li>
 *   <li>GET  /api/v1/payments — payment history paginated (PYMT-05)</li>
 * </ul>
 *
 * <p>userId is always extracted from the JWT subject claim — never from the request body or
 * path parameter (ASVS V4, IDOR prevention).
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.payment.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * Initiates an Azampay STK push for the authenticated user.
     *
     * @param auth JWT authentication — userId extracted from subject
     * @param request purchase request (bundleId, msisdn, provider)
     * @return PaymentDto with status PENDING and 120-second timeout hint (PYMT-03)
     */
    @PostMapping
    public ResponseEntity<PaymentDto> initiate(
            JwtAuthenticationToken auth,
            @Valid @RequestBody PurchaseRequest request) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        Payment payment = paymentService.initiate(userId, request);
        return ResponseEntity.ok(PaymentDto.from(payment, timeoutSeconds));
    }

    /**
     * Returns the authenticated user's payment history, newest-first (PYMT-05).
     *
     * @param auth JWT authentication — userId extracted from subject
     * @return paginated PaymentDto list (only this user's payments — no IDOR)
     */
    @GetMapping
    public Page<PaymentDto> history(
            JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return paymentService.history(userId, PageRequest.of(page, size))
                .map(p -> PaymentDto.from(p, timeoutSeconds));
    }
}
