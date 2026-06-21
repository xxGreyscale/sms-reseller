package com.opendesk.messaging.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stub delivery receipt (DLR) webhook endpoint.
 *
 * <p>Delegates to {@link DeliveryReceiptService#handleDeliveryReceipt} — the same handler
 * used by the {@link com.opendesk.messaging.sms.StubSmsProvider} @Scheduled sweep.
 *
 * <p>MVP note: this endpoint accepts unauthenticated POST (the real provider webhook will
 * not carry a JWT). T-04-16 (Spoofing) is accepted risk at MVP — real signature verification
 * deferred to when the production SMS provider is contracted (04-VALIDATION manual row).
 *
 * <p>The endpoint is permitted in SecurityConfig (/api/v1/messaging/dlr → permitAll) to allow
 * provider callbacks without authentication. It is intentionally scope-limited to DLR only.
 */
@RestController
@RequestMapping("/api/v1/messaging/dlr")
@RequiredArgsConstructor
@Slf4j
public class DlrController {

    private final DeliveryReceiptService deliveryReceiptService;

    /**
     * Receive a delivery receipt from the SMS provider.
     *
     * <p>Expected body: {@code {"externalId": "...", "status": "DELIVERED"|"FAILED"}}.
     * Responds 200 OK regardless of whether the message was found (to avoid provider retries).
     */
    @PostMapping
    public ResponseEntity<Void> receiveDlr(@RequestBody Map<String, String> body) {
        String externalId = body.get("externalId");
        String status = body.getOrDefault("status", "DELIVERED");

        if (externalId == null || externalId.isBlank()) {
            log.warn("DlrController: received DLR with missing externalId — ignoring");
            return ResponseEntity.badRequest().build();
        }

        log.info("DlrController: DLR received externalId={} status={}", externalId, status);
        deliveryReceiptService.handleDeliveryReceipt(externalId, status);
        return ResponseEntity.ok().build();
    }
}
