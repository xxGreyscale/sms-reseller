package com.smsreseller.payment.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Azampay webhook callback endpoint.
 *
 * <p>This endpoint is {@code permitAll} in SecurityConfig (no JWT required — Azampay does not
 * provide a JWT). Authentication is via webhook signature validation ({@link WebhookSignatureValidator}).
 *
 * <p>Returns 200 OK always — even for invalid/unknown callbacks — to prevent Azampay from
 * retrying indefinitely. Invalid callbacks are logged and ignored by {@link CallbackProcessor}.
 * Unknown {@code utilityRef} values are rejected at the processor level (T-03-12).
 */
@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
@Slf4j
public class CallbackController {

    private final CallbackProcessor callbackProcessor;
    private final WebhookSignatureValidator signatureValidator;

    /**
     * Receives Azampay STK push callback.
     *
     * <p>No {@code JwtAuthenticationToken} parameter — this is a public endpoint.
     *
     * @param payload Azampay callback JSON body
     * @param headers HTTP headers (may contain Azampay signature)
     * @return 200 OK
     */
    @PostMapping
    public ResponseEntity<Void> handleCallback(
            @RequestBody AzampayCallbackPayload payload,
            @RequestHeader HttpHeaders headers) {

        if (!signatureValidator.isValid(payload, headers)) {
            log.warn("CallbackController: invalid signature for utilityRef={} — ignoring (T-03-12)",
                    payload.utilityRef());
            return ResponseEntity.ok().build();  // 200 to prevent Azampay retrying
        }

        callbackProcessor.processCallback(payload);
        return ResponseEntity.ok().build();
    }
}
