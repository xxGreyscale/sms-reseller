package com.smsreseller.payment.reconciliation;

import com.smsreseller.payment.callback.CallbackProcessor;
import com.smsreseller.payment.callback.AzampayCallbackPayload;
import com.smsreseller.payment.gateway.PaymentGateway;
import com.smsreseller.payment.gateway.TransactionStatusResult;
import com.smsreseller.payment.payment.Payment;
import com.smsreseller.payment.payment.PaymentRepository;
import com.smsreseller.payment.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled reconciliation job for stale PENDING and EXPIRED payments (D-04, CLAUDE.md).
 *
 * <p>Polls Azampay every 2 minutes (fixedDelay) for payments older than 5 minutes that
 * are still in PENDING or EXPIRED state. If Azampay confirms success, drives the same
 * idempotent late-success path as {@link CallbackProcessor} (flip to SUCCESS + PaymentConfirmed
 * outbox event). Prevents lost PaymentConfirmed events for payments where Azampay callback
 * was not delivered (T-03-18, D-04).
 *
 * <p>Pattern: VerificationRetryJob analog — bounded query, early-empty exit, per-item try/catch.
 * The scheduled method delegates to {@link #reconcile(Instant)} to allow test fast-forward.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final CallbackProcessor callbackProcessor;

    @Value("${app.reconciliation.max-per-run:50}")
    private int maxPerRun;

    /**
     * @Scheduled entry point — uses real now()-5min cutoff.
     * Delegates to {@link #reconcile(Instant)} for testability.
     */
    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:120000}")
    public void scheduleReconcile() {
        reconcile(Instant.now().minus(5, ChronoUnit.MINUTES));
    }

    /**
     * Reconciles stale PENDING/EXPIRED payments created before {@code cutoff}.
     *
     * <p>Exposed for tests — pass a future cutoff to include recently-created payments.
     *
     * @param cutoff payments created before this instant are eligible for reconciliation
     */
    @Transactional
    public void reconcile(Instant cutoff) {
        List<Payment> stale = paymentRepository.findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.EXPIRED),
                cutoff,
                PageRequest.of(0, maxPerRun));

        if (stale.isEmpty()) {
            log.debug("ReconciliationJob: no stale payments to reconcile");
            return;
        }

        log.info("ReconciliationJob: checking {} stale payment(s)", stale.size());

        for (Payment payment : stale) {
            try {
                checkAndReconcile(payment);
            } catch (Exception ex) {
                log.warn("ReconciliationJob: error checking paymentId={}", payment.getId(), ex);
            }
        }
    }

    private void checkAndReconcile(Payment payment) {
        if (payment.getExternalId() == null) {
            log.debug("ReconciliationJob: payment {} has no externalId — skipping", payment.getId());
            return;
        }

        TransactionStatusResult result =
                paymentGateway.queryTransactionStatus(payment.getExternalId());

        if (result.isSuccess()) {
            // Drive the idempotent late-success path via CallbackProcessor (D-04, Pitfall 5)
            // CallbackProcessor guards: status==SUCCESS → skip (idempotent second run)
            AzampayCallbackPayload callbackPayload = new AzampayCallbackPayload(
                    null,                                      // msisdn
                    null,                                      // amount
                    "reconciliation late-success",             // message
                    payment.getExternalId(),                   // utilityRef
                    null,                                      // operator
                    "reconciliation-" + payment.getId(),       // reference
                    "success",                                 // transactionStatus
                    null                                       // submerchantAcc
            );
            callbackProcessor.processCallback(callbackPayload);
            log.info("ReconciliationJob: late-success for paymentId={}", payment.getId());
        } else if (result.isFailed()) {
            log.info("ReconciliationJob: Azampay reports FAILED for paymentId={} — leaving as-is for callback",
                    payment.getId());
            // Let the callback handle it — reconciliation only rescues confirmed payments
        } else {
            log.debug("ReconciliationJob: payment {} still pending at Azampay", payment.getId());
        }
    }
}
