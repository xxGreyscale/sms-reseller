package com.smsreseller.identity.verification;

import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job that re-dispatches NIDA verification for users stuck in PENDING status.
 *
 * <p>IDEN-08: When NIDA is unavailable at registration time, the user stays PENDING.
 * This job polls for users that have been PENDING for longer than {@code retryAfterMinutes}
 * and re-dispatches {@link VerificationOrchestrator#verifyAsync(java.util.UUID, String)}.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Bounded query — returns at most {@code maxPerRun} users per tick to avoid
 *       overwhelming the nidaExecutor pool during bulk recovery after a NIDA outage.</li>
 *   <li>The NIN is not stored. In this MVP implementation, the orchestrator is re-dispatched
 *       with a placeholder NIN of {@code null} — the stub handles this; the real impl
 *       would need to store an encrypted NIN or have a different recovery mechanism
 *       (deferred to a future plan once the NIDA API contract is confirmed).</li>
 *   <li>The job runs every {@code fixedDelayMs} ms. At 1 replica (MVP) there is no
 *       distributed locking concern; add ShedLock when scaling to multiple replicas.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationRetryJob {

    private final UserRepository userRepository;
    private final VerificationOrchestrator verificationOrchestrator;

    @Value("${app.nida.retry.after-minutes:10}")
    private int retryAfterMinutes;

    @Value("${app.nida.retry.max-per-run:20}")
    private int maxPerRun;

    /**
     * Re-dispatch NIDA verification for PENDING users older than {@code retryAfterMinutes}.
     *
     * <p>Runs every 2 minutes. The fixedDelay (not fixedRate) ensures the previous run
     * completes before the next starts, preventing overlapping jobs.
     */
    @Scheduled(fixedDelayString = "${app.nida.retry.fixed-delay-ms:120000}")
    public void retryPendingVerifications() {
        Instant cutoff = Instant.now().minus(retryAfterMinutes, ChronoUnit.MINUTES);

        List<User> pendingUsers = userRepository
                .findByStatusAndCreatedAtBefore(
                        VerificationStatus.PENDING_VERIFICATION,
                        cutoff,
                        PageRequest.of(0, maxPerRun));

        if (pendingUsers.isEmpty()) {
            log.debug("VerificationRetryJob: no PENDING users older than {} minutes", retryAfterMinutes);
            return;
        }

        log.info("VerificationRetryJob: re-dispatching {} PENDING verification(s)", pendingUsers.size());

        for (User user : pendingUsers) {
            // NIN is not stored — re-dispatch with null NIN (stub handles this;
            // real impl would use encrypted stored NIN — deferred to post-NIDA-API-confirmation)
            verificationOrchestrator.verifyAsync(user.getId(), null);
        }
    }
}
