package com.opendesk.payment.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for payment endpoints.
 *
 * <p>timeoutSeconds (PYMT-03 countdown contract): always 120 for PENDING payments —
 * tells the client how long to wait before the payment transitions to EXPIRED.
 */
public record PaymentDto(
        UUID paymentId,
        UUID bundleId,
        long amountTzs,
        int smsCount,
        String status,
        Instant createdAt,
        int timeoutSeconds
) {

    public static PaymentDto from(Payment payment, int timeoutSeconds) {
        return new PaymentDto(
                payment.getId(),
                payment.getBundleId(),
                payment.getAmountTzs(),
                payment.getSmsCount(),
                payment.getStatus().name(),
                payment.getCreatedAt(),
                timeoutSeconds
        );
    }
}
