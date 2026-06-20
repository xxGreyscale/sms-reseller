package com.opendesk.wallet.transaction;

/**
 * Credit transaction types for the append-only ledger.
 *
 * <ul>
 *   <li>{@code GRANT}   — credits added to a lot (purchase, bonus, refund lot creation)</li>
 *   <li>{@code RESERVE} — credits locked for an in-flight campaign</li>
 *   <li>{@code CONSUME} — credits debited on campaign send confirmation</li>
 *   <li>{@code RELEASE} — reserved credits returned (campaign cancelled before send)</li>
 *   <li>{@code EXPIRE}  — remaining credits zeroed on lot expiry</li>
 *   <li>{@code REFUND}  — new REFUND lot created; cross-referenced to original lot</li>
 * </ul>
 */
public enum TxnType {
    GRANT,
    RESERVE,
    CONSUME,
    RELEASE,
    EXPIRE,
    REFUND
}
