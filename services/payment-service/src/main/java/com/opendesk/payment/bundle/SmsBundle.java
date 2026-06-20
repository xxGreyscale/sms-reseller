package com.opendesk.payment.bundle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * SMS bundle catalog entity (PYMT-01, D-09, D-12).
 *
 * <p>Seeded via V2__seed_sms_bundles.sql Flyway migration.
 * Editable via admin UI (Phase 5) without code change.
 *
 * <p>Price is stored as raw TZS whole shillings ({@code long}) per D-11.
 * Never use {@code BigDecimal} or {@code Double} for TZS amounts (Pitfall 7).
 */
@Entity
@Table(name = "sms_bundles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsBundle {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "sms_count", nullable = false)
    private int smsCount;

    /**
     * Price in raw TZS whole shillings. 0 = free (Taster).
     * D-11: stored as BIGINT raw shillings — no ×100 "cents" scaling.
     */
    @Column(name = "price_tzs", nullable = false)
    private long priceTzs;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * Whether this bundle can be purchased via Azampay STK push.
     * Taster is false — granted on NIDA verification, not purchased.
     */
    @Column(name = "is_purchasable", nullable = false)
    private boolean isPurchasable;

    @Column(name = "description")
    private String description;
}
