package com.smsreseller.wallet.refund;

import com.smsreseller.wallet.consumer.ProcessedEventRepository;
import com.smsreseller.wallet.lot.LotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent credit-back service for failed campaign refunds (D-07, PYMT-08).
 *
 * <p>Creates an append-only REFUND credit lot. The {@code idempotencyKey} is stored in
 * {@code processed_events} under a {@code "refund:"} prefix to prevent double-credit on retry.
 * Calling {@link #refund} twice with the same key is a no-op on the second call (T-03-17).
 *
 * <p>This service is called by Phase 4 campaign dispatch — the caller supplies the idempotency key
 * to ensure safe retries across service restarts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final ProcessedEventRepository processedEventRepository;
    private final LotService lotService;

    /**
     * Credits {@code credits} back to the user's wallet as a REFUND lot.
     *
     * <p>Idempotency: the composite key {@code "refund:" + idempotencyKey} is inserted into
     * {@code processed_events} via {@code ON CONFLICT DO NOTHING}. If the key already exists,
     * the refund is silently skipped — the credit is not applied again.
     *
     * @param userId         user to credit
     * @param credits        number of credits to credit back — must be positive (T-03-17)
     * @param referenceId    campaign or payment reference that triggered the refund
     * @param idempotencyKey caller-supplied unique key for this refund operation
     * @throws IllegalArgumentException if credits is zero or negative (T-03-17)
     */
    @Transactional
    public void refund(UUID userId, int credits, UUID referenceId, String idempotencyKey) {
        if (credits <= 0) {
            throw new IllegalArgumentException(
                    "Refund credits must be positive; got " + credits + " (T-03-17)");
        }

        String guard = "refund:" + idempotencyKey;
        if (!processedEventRepository.tryInsert(guard)) {
            log.debug("RefundService: duplicate idempotencyKey={} — skipping (T-03-17)", idempotencyKey);
            return;
        }

        lotService.creditBack(userId, credits, referenceId);
        log.info("RefundService: credited {} REFUND credits to userId={} referenceId={}",
                credits, userId, referenceId);
    }
}
