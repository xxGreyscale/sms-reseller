package com.smsreseller.payment.timeout;

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
 * Scheduled job that marks PENDING payments as EXPIRED after the configured timeout.
 *
 * <p>Implements PYMT-03/07 and D-06/T-03-13:
 * <ul>
 *   <li>PENDING payments older than {@code app.payment.timeout-seconds} (120s) are marked EXPIRED</li>
 *   <li>EXPIRED is a terminal-or-recoverable state — the UI stops spinning (no infinite PENDING)</li>
 *   <li>EXPIRED payments may still transition to SUCCESS via a late callback (D-04) — reconciliation
 *       is handled by CallbackProcessor and future ReconciliationJob</li>
 * </ul>
 *
 * <p>Pattern: mirrors {@code VerificationRetryJob} from identity-service (03-PATTERNS.md lines 295-351).
 * Uses {@code fixedDelay} (not {@code fixedRate}) so run completes before next cycle starts.
 * Per-item try/catch prevents one failure from blocking the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTimeoutJob {

    private final PaymentRepository paymentRepository;

    @Value("${app.payment.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${app.payment.timeout-sweep-ms:30000}")
    private long timeoutSweepMs;

    @Value("${app.payment.timeout-max-per-run:100}")
    private int maxPerRun;

    /**
     * Scheduled sweep: marks PENDING payments older than timeout as EXPIRED.
     *
     * <p>Cutoff = now minus timeout-seconds. All PENDING payments created before the cutoff
     * are transitioned to EXPIRED in a bounded batch.
     */
    @Scheduled(fixedDelayString = "${app.payment.timeout-sweep-ms:30000}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
        sweepExpiredPayments(cutoff);
    }

    /**
     * Exposed for testing — allows callers to provide a custom cutoff (e.g. "future" cutoff
     * to make recently-created payments appear stale in tests).
     *
     * @param cutoff payments created before this instant are eligible for expiry
     */
    @Transactional
    public void sweepExpiredPayments(Instant cutoff) {
        List<Payment> stale = paymentRepository.findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING),
                cutoff,
                PageRequest.of(0, maxPerRun)
        );

        if (stale.isEmpty()) {
            log.debug("PaymentTimeoutJob: no stale PENDING payments found (cutoff={})", cutoff);
            return;
        }

        log.info("PaymentTimeoutJob: expiring {} stale PENDING payment(s) (cutoff={})", stale.size(), cutoff);

        for (Payment payment : stale) {
            try {
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);
                log.debug("PaymentTimeoutJob: expired paymentId={} userId={}", payment.getId(), payment.getUserId());
            } catch (Exception ex) {
                log.warn("PaymentTimeoutJob: failed to expire paymentId={}", payment.getId(), ex);
            }
        }
    }
}
