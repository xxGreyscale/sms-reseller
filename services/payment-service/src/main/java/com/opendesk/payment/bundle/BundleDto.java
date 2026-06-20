package com.opendesk.payment.bundle;

import java.util.UUID;

/**
 * Read-only DTO for the bundle catalog API (GET /api/v1/bundles).
 *
 * <p>Exposed fields: id, name, smsCount, priceTzs (raw TZS, D-11), isPurchasable, description.
 * The {@code isActive} field is intentionally excluded — inactive bundles are filtered at
 * the repository level and never returned to clients.
 *
 * @param id            bundle UUID
 * @param name          display name (e.g. "Starter")
 * @param smsCount      number of SMS credits in this bundle
 * @param priceTzs      price in raw TZS whole shillings (D-11); 0 = free (Taster)
 * @param isPurchasable whether this bundle can be bought via Azampay STK push
 * @param description   optional description (null for most bundles)
 */
public record BundleDto(
        UUID id,
        String name,
        int smsCount,
        long priceTzs,
        boolean isPurchasable,
        String description
) {

    /**
     * Maps a {@link SmsBundle} entity to a read-only DTO.
     */
    public static BundleDto from(SmsBundle bundle) {
        return new BundleDto(
                bundle.getId(),
                bundle.getName(),
                bundle.getSmsCount(),
                bundle.getPriceTzs(),
                bundle.isPurchasable(),
                bundle.getDescription()
        );
    }
}
