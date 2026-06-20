package com.opendesk.wallet.lot;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only credit lot. One row per credit grant event (purchase, bonus, refund).
 *
 * <p>Balance is derived: {@code SUM(granted - consumed - reserved)} over non-expired lots.
 * The {@code reserved} field is mutated in-place only by {@code ReservationService} under
 * a pessimistic write lock (D-02, SELECT FOR UPDATE). {@code consumed} is updated when a
 * campaign is confirmed sent.
 *
 * <p>There is intentionally no mutable {@code balance} column (D-02).
 */
@Entity
@Table(name = "credit_lots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLot {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_type", nullable = false, updatable = false)
    private LotType lotType;

    /** Credits originally granted — immutable after insert. */
    @Column(name = "granted", nullable = false, updatable = false)
    private int granted;

    /** Credits fully debited by confirmed campaign sends. */
    @Column(name = "consumed", nullable = false)
    private int consumed;

    /** Credits held for in-flight campaigns (pessimistic write lock). */
    @Column(name = "reserved", nullable = false)
    private int reserved;

    /** Expiry timestamp — lots past this instant are excluded from balance (D-03). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** FK to payments.id — null for BONUS and REFUND lots. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
