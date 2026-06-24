package com.smsreseller.messaging.scheduler;

import com.smsreseller.messaging.campaign.Campaign;
import com.smsreseller.messaging.campaign.CampaignRepository;
import com.smsreseller.messaging.campaign.CampaignService;
import com.smsreseller.messaging.campaign.CampaignStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * DB-polled scheduler that dispatches campaigns at/after their scheduled time (MESG-04, D-10).
 *
 * <p>Mirrors payment-service ReconciliationJob: {@code schedule()} is the real cron entry-point
 * that delegates to the testable {@code dispatch(Instant now)} method, allowing unit tests to
 * fast-forward time without waiting for the real timer.
 *
 * <p>Batch size is capped at 50 per poll tick to avoid long-running transactions.
 * Per-item try/catch ensures one failing campaign does not abort the entire batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledCampaignDispatchJob {

    private static final int BATCH_SIZE = 50;

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;

    /**
     * Real {@code @Scheduled} entry-point — fires every 30 seconds.
     * Delegates to {@link #dispatch(Instant)} with the current wall-clock time.
     */
    @Scheduled(fixedDelay = 30_000)
    public void schedule() {
        dispatch(Instant.now());
    }

    /**
     * Testable delegate: queries for SCHEDULED campaigns whose {@code scheduledAt} is before
     * {@code now}, then calls {@link CampaignService#executeSend(Campaign)} on each.
     *
     * <p>T-04-18: only SCHEDULED campaigns are returned — CANCELLED campaigns are excluded
     * because they already transitioned out of SCHEDULED before this poll fires.
     *
     * @param now the reference instant to use as the cutoff (substitutable in tests)
     */
    public void dispatch(Instant now) {
        List<Campaign> due = campaignRepository.findByStatusAndScheduledAtBefore(
                CampaignStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE));

        if (due.isEmpty()) {
            return;
        }

        log.info("ScheduledCampaignDispatchJob: found {} due campaigns", due.size());

        for (Campaign campaign : due) {
            try {
                campaignService.executeSend(campaign);
                log.info("Dispatched scheduled campaign id={}", campaign.getId());
            } catch (Exception e) {
                log.error("Failed to dispatch scheduled campaign id={}: {}", campaign.getId(), e.getMessage(), e);
            }
        }
    }
}
