package com.smsreseller.payment.domain.payment;

/**
 * Payment lifecycle states (D-06).
 *
 * <p>State machine:
 * <pre>
 *   PENDING ──→ SUCCESS
 *           ──→ EXPIRED ──→ SUCCESS  (via reconciliation, D-04)
 *           ──→ FAILED
 * </pre>
 *
 * <p>No infinite spinner: the 2-minute countdown (D-06) always resolves to a terminal state.
 * EXPIRED → SUCCESS is the late-success path when Azampay confirms after our timeout window.
 */
public enum PaymentStatus {

    /**
     * STK push initiated; awaiting Azampay callback or reconciliation.
     * Enforced: only one PENDING payment per user at a time (D-05, partial unique index D-13).
     */
    PENDING,

    /**
     * Payment confirmed by Azampay — wallet credited (Plan 05).
     */
    SUCCESS,

    /**
     * No callback received within the 2-minute timeout window (D-06).
     * May transition to SUCCESS via reconciliation job (D-04) if Azampay later confirms.
     */
    EXPIRED,

    /**
     * Azampay rejected or user cancelled — no credit granted.
     */
    FAILED
}
