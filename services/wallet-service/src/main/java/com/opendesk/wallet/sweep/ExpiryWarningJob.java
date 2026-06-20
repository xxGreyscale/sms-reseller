package com.opendesk.wallet.sweep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.wallet.consumer.ProcessedEventRepository;
import com.opendesk.wallet.lot.CreditLot;
import com.opendesk.wallet.lot.CreditLotRepository;
import com.opendesk.wallet.lot.LotType;
import com.opendesk.wallet.outbox.OutboxEntry;
import com.opendesk.wallet.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that emits an {@code ExpiryWarning} outbox event for PURCHASED credit lots
 * that will expire within 7 days (WLET-05).
 *
 * <p>Dedup: per-lot key {@code "expiry-warning:" + lotId} stored in processed_events.
 * A lot that has already been warned will not produce a second warning (T-03-20 pattern).
 *
 * <p>Follows VerificationRetryJob pattern: bounded query, early-empty exit, per-item try/catch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryWarningJob {

    private static final int MAX_PER_RUN = 100;
    private static final String EVENT_TYPE = "ExpiryWarning";
    private static final String AGGREGATE_TYPE = "Wallet";

    private final CreditLotRepository creditLotRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${app.wallet.expiry-warning-cron:0 0 8 * * ?}")
    @Transactional
    public void warnExpiringSoon() {
        Instant now = Instant.now();
        Instant cutoff = now.plus(7, ChronoUnit.DAYS);

        List<CreditLot> soonExpiring = creditLotRepository.findExpiringBefore(
                now, cutoff, LotType.PURCHASED, PageRequest.of(0, MAX_PER_RUN));

        if (soonExpiring.isEmpty()) {
            log.debug("ExpiryWarningJob: no PURCHASED lots expiring within 7 days");
            return;
        }

        log.info("ExpiryWarningJob: {} lot(s) expiring within 7 days", soonExpiring.size());

        for (CreditLot lot : soonExpiring) {
            try {
                emitWarningIfNotAlreadySent(lot);
            } catch (Exception ex) {
                log.warn("ExpiryWarningJob: error processing lotId={}", lot.getId(), ex);
            }
        }
    }

    private void emitWarningIfNotAlreadySent(CreditLot lot) {
        // Dedup per lot — permanent key so the same lot is never warned twice
        String warningKey = "expiry-warning:" + lot.getId();
        if (!processedEventRepository.tryInsert(warningKey)) {
            log.debug("ExpiryWarningJob: already warned lotId={} — skipping", lot.getId());
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "lotId", lot.getId().toString(),
                    "userId", lot.getUserId().toString(),
                    "expiresAt", lot.getExpiresAt().toString(),
                    "remainingCredits", lot.getGranted() - lot.getConsumed() - lot.getReserved()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ExpiryWarning payload for lotId=" + lot.getId(), e);
        }

        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(lot.getUserId().toString())
                .eventType(EVENT_TYPE)
                .payload(payload)
                .build();
        outboxRepository.save(entry);

        log.info("ExpiryWarningJob: emitted ExpiryWarning for lotId={} userId={} expiresAt={}",
                lot.getId(), lot.getUserId(), lot.getExpiresAt());
    }
}
