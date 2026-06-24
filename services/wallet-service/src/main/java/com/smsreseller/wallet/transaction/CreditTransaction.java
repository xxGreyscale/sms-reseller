package com.smsreseller.wallet.transaction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable append-only ledger entry. One row per credit movement event on a lot.
 *
 * <p>No {@code @Setter} — this entity is fully immutable once persisted. Use the
 * all-args constructor or the convenience constructor to create instances.
 */
@Entity
@Table(name = "credit_transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class CreditTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, updatable = false)
    private TxnType txnType;

    /** Always positive — direction is conveyed by {@code txnType}. */
    @Column(name = "delta", nullable = false, updatable = false)
    private int delta;

    /** campaign_id for RESERVE/CONSUME; payment_id for GRANT/REFUND; null for EXPIRE. */
    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public CreditTransaction(UUID userId, UUID lotId, TxnType txnType, int delta, UUID referenceId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.lotId = lotId;
        this.txnType = txnType;
        this.delta = delta;
        this.referenceId = referenceId;
    }
}
