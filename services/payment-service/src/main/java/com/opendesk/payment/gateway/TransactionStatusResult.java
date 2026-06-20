package com.opendesk.payment.gateway;

/**
 * Result of querying an Azampay transaction status (reconciliation / polling).
 *
 * <p>Three terminal-or-pending states:
 * <ul>
 *   <li>{@link #success} — payment confirmed; wallet should be credited</li>
 *   <li>{@link #failed} — payment failed or cancelled; no credit</li>
 *   <li>{@link #pending} — Azampay hasn't confirmed yet; retry later (stub TIMEOUT outcome)</li>
 * </ul>
 */
public final class TransactionStatusResult {

    private enum Outcome { SUCCESS, FAILED, PENDING }

    private final Outcome outcome;
    private final String externalId;

    private TransactionStatusResult(Outcome outcome, String externalId) {
        this.outcome = outcome;
        this.externalId = externalId;
    }

    /** Factory: Azampay confirmed the payment as successful. */
    public static TransactionStatusResult success(String externalId) {
        return new TransactionStatusResult(Outcome.SUCCESS, externalId);
    }

    /** Factory: Azampay confirmed the payment as failed or cancelled. */
    public static TransactionStatusResult failed(String externalId) {
        return new TransactionStatusResult(Outcome.FAILED, externalId);
    }

    /** Factory: Azampay has not yet resolved the payment (poll again later). */
    public static TransactionStatusResult pending(String externalId) {
        return new TransactionStatusResult(Outcome.PENDING, externalId);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    public boolean isPending() {
        return outcome == Outcome.PENDING;
    }

    public String getExternalId() {
        return externalId;
    }
}
