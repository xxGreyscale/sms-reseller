package com.smsreseller.wallet.lot;

/**
 * Type of credit lot — determines expiry window and origin of the grant.
 *
 * <ul>
 *   <li>{@code PURCHASED} — from a user payment; expires 12 months from purchase date (D-03)</li>
 *   <li>{@code BONUS} — NIDA verification grant or promotional credits; expires 30 days (D-03)</li>
 *   <li>{@code REFUND} — credit-back for a failed campaign; inherits remaining expiry of source lot</li>
 * </ul>
 */
public enum LotType {
    PURCHASED,
    BONUS,
    REFUND
}
