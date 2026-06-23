package com.smsreseller.wallet.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO for exposing a {@link CreditTransaction} via the wallet API (WLET-02).
 *
 * <p>Never exposes the full entity — only fields relevant to the user-facing history view.
 *
 * @param id          transaction UUID
 * @param lotId       the credit lot this transaction belongs to
 * @param txnType     type of transaction (GRANT, RESERVE, CONSUME, RELEASE, EXPIRE, REFUND)
 * @param delta       magnitude of the credit movement (always positive — direction via txnType)
 * @param referenceId optional reference (campaign ID for RESERVE/CONSUME, payment ID for GRANT)
 * @param createdAt   when this transaction was created (append-only timestamp)
 */
public record CreditTransactionDto(
        UUID id,
        UUID lotId,
        TxnType txnType,
        int delta,
        UUID referenceId,
        Instant createdAt
) {

    /**
     * Creates a DTO from a {@link CreditTransaction} entity.
     *
     * @param entity the source entity
     * @return immutable DTO
     */
    public static CreditTransactionDto from(CreditTransaction entity) {
        return new CreditTransactionDto(
                entity.getId(),
                entity.getLotId(),
                entity.getTxnType(),
                entity.getDelta(),
                entity.getReferenceId(),
                entity.getCreatedAt()
        );
    }
}
