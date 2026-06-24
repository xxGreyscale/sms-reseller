package com.smsreseller.wallet.sweep;

import com.smsreseller.wallet.lot.CreditLot;
import com.smsreseller.wallet.lot.CreditLotRepository;
import com.smsreseller.wallet.transaction.CreditTransaction;
import com.smsreseller.wallet.transaction.CreditTransactionRepository;
import com.smsreseller.wallet.transaction.TxnType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that enforces lot expiry by writing EXPIRE credit transactions for lots
 * whose {@code expires_at} timestamp has passed (belt-and-suspenders beyond the balance filter).
 *
 * <p>The derived balance query already filters out expired lots via {@code expiresAt > now}.
 * This sweep writes explicit EXPIRE transactions to make the lot's lifecycle visible in the
 * transaction ledger — useful for audit trails and reconciliation (WLET-05).
 *
 * <p>Follows VerificationRetryJob pattern: bounded query, early-empty exit, per-item try/catch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpirySweepJob {

    private static final int MAX_PER_RUN = 200;

    private final CreditLotRepository creditLotRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Scheduled(cron = "${app.wallet.expiry-sweep-cron:0 0 2 * * ?}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        List<CreditLot> expiredLots = creditLotRepository.findExpiredBefore(
                now, PageRequest.of(0, MAX_PER_RUN));

        if (expiredLots.isEmpty()) {
            log.debug("ExpirySweepJob: no lots to sweep");
            return;
        }

        log.info("ExpirySweepJob: sweeping {} expired lot(s)", expiredLots.size());

        for (CreditLot lot : expiredLots) {
            try {
                writeExpireTransaction(lot);
            } catch (Exception ex) {
                log.warn("ExpirySweepJob: error sweeping lotId={}", lot.getId(), ex);
            }
        }
    }

    private void writeExpireTransaction(CreditLot lot) {
        // Check if an EXPIRE transaction already exists for this lot (idempotency for re-runs)
        boolean alreadyExpired = creditTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(lot.getUserId(), PageRequest.of(0, 100))
                .stream()
                .anyMatch(t -> TxnType.EXPIRE.equals(t.getTxnType()) && lot.getId().equals(t.getLotId()));

        if (alreadyExpired) {
            log.debug("ExpirySweepJob: lot {} already has EXPIRE transaction — skipping", lot.getId());
            return;
        }

        int remainingCredits = lot.getGranted() - lot.getConsumed() - lot.getReserved();
        if (remainingCredits <= 0) {
            log.debug("ExpirySweepJob: lot {} has no remaining credits — no EXPIRE tx needed", lot.getId());
            return;
        }

        creditTransactionRepository.save(new CreditTransaction(
                lot.getUserId(), lot.getId(), TxnType.EXPIRE, remainingCredits, null));

        log.info("ExpirySweepJob: wrote EXPIRE transaction for lotId={} userId={} remaining={}",
                lot.getId(), lot.getUserId(), remainingCredits);
    }
}
