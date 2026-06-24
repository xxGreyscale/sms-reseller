package com.smsreseller.wallet.admin;

import com.smsreseller.wallet.transaction.CreditTransaction;
import com.smsreseller.wallet.transaction.TxnType;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the admin ledger inspection endpoint (ADMN-03).
 *
 * <p>Exposes the UI-SPEC ledger columns: date, type, description, delta, and referenceId.
 * BalanceAfter is not computed here — the append-only ledger tracks delta magnitudes; a
 * running balance would require a full scan and is deferred to the client or a future view.
 *
 * @param id          transaction UUID
 * @param date        timestamp of the transaction
 * @param txnType     GRANT / RESERVE / CONSUME / RELEASE / EXPIRE / REFUND
 * @param description human-readable label derived from txnType
 * @param delta       magnitude of the credit movement (always positive)
 * @param referenceId optional campaign ID (RESERVE/CONSUME) or payment ID (GRANT/REFUND)
 */
public record LedgerEntryDto(
        UUID id,
        Instant date,
        TxnType txnType,
        String description,
        int delta,
        UUID referenceId
) {

    public static LedgerEntryDto from(CreditTransaction entity) {
        return new LedgerEntryDto(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getTxnType(),
                descriptionFor(entity.getTxnType()),
                entity.getDelta(),
                entity.getReferenceId()
        );
    }

    private static String descriptionFor(TxnType type) {
        return switch (type) {
            case GRANT   -> "Credits granted";
            case RESERVE -> "Credits reserved for campaign";
            case CONSUME -> "Credits consumed by campaign";
            case RELEASE -> "Reserved credits released";
            case EXPIRE  -> "Credits expired";
            case REFUND  -> "Credits refunded";
        };
    }
}
