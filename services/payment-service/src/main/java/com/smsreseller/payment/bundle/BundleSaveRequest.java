package com.smsreseller.payment.bundle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Validated DTO for admin create/update of SMS bundles (ADMN-07).
 *
 * <p>Threat model T-05-13: non-positive price or smsCount is rejected by Bean Validation
 * before reaching the service layer — no invalid row written to sms_bundles.
 *
 * @param name      display name — must not be blank
 * @param smsCount  number of SMS credits — minimum 1 (T-05-13)
 * @param priceTzs  price in raw TZS whole shillings (D-11) — must be positive (T-05-13)
 * @param active    whether the bundle is visible in the customer catalog
 */
public record BundleSaveRequest(
        @NotBlank String name,
        @Min(1) int smsCount,
        @Positive long priceTzs,
        boolean active
) {}
