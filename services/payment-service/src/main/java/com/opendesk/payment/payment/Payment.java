package com.opendesk.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment entity — tracks Azampay STK push payments (PYMT-02 to PYMT-07, Plan 05).
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>D-05/D-13: Only one PENDING payment per user enforced by DB partial unique index
 *       ({@code uq_payments_user_pending} — V3 migration). No Redis lock needed.</li>
 *   <li>D-06: State machine: PENDING → SUCCESS | EXPIRED | FAILED; EXPIRED may → SUCCESS
 *       via reconciliation (D-04).</li>
 *   <li>D-11: {@code amountTzs} is raw TZS whole shillings as {@code long} — integer-safe (T-03-07).</li>
 *   <li>{@code externalId} = payment UUID string (Azampay idempotency key) — UNIQUE constraint
 *       in DB prevents duplicate STK pushes.</li>
 * </ul>
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "bundle_id", nullable = false, updatable = false)
    private UUID bundleId;

    /**
     * Amount in raw TZS whole shillings (D-11). Copied from bundle at purchase time.
     * Stored as BIGINT → {@code long}. Integer-safe for TZS amounts (T-03-07).
     */
    @Column(name = "amount_tzs", nullable = false, updatable = false)
    private long amountTzs;

    @Column(name = "sms_count", nullable = false, updatable = false)
    private int smsCount;

    /**
     * Payment lifecycle state (D-06). Stored as string per {@code @Enumerated(EnumType.STRING)}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    /**
     * Azampay idempotency key = payment UUID as string.
     * UNIQUE constraint in DB; set at initiation; null before first STK push attempt.
     */
    @Column(name = "external_id", unique = true)
    private String externalId;

    /**
     * Azampay operator transaction reference returned on callback (nullable until callback).
     */
    @Column(name = "operator_reference")
    private String operatorReference;

    /**
     * Mobile money provider (e.g. "MPESA", "TIGOPESA", "AIRTELMONEY", "AZAMPESA").
     * Set at initiation based on the user's MSISDN prefix.
     */
    @Column(name = "provider", nullable = false)
    private String provider;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
