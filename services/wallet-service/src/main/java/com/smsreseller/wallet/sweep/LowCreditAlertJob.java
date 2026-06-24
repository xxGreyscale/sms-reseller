package com.smsreseller.wallet.sweep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.wallet.consumer.ProcessedEventRepository;
import com.smsreseller.wallet.lot.CreditLotRepository;
import com.smsreseller.wallet.outbox.OutboxEntry;
import com.smsreseller.wallet.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that emits a {@code LowCreditAlert} outbox event for users whose available
 * balance is below the configured threshold (D-08: default 20 credits, WLET-04).
 *
 * <p>Runs every ~5 minutes (fixedDelay). Per-user dedup: the alert key
 * {@code "low-credit-alert:" + userId} is stored in processed_events so the same user
 * is not re-alerted in the same cycle. The cycle key is refreshed on the next scheduled
 * invocation only if the previous entry has aged out (MVP-simple: permanent dedup per userId
 * until manually cleared — acceptable at MVP per D-08 / T-03-20 accept disposition).
 *
 * <p>Follows VerificationRetryJob pattern: bounded query, early-empty exit, per-item try/catch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LowCreditAlertJob {

    private static final int MAX_PER_RUN = 100;
    private static final String EVENT_TYPE = "LowCreditAlert";
    private static final String AGGREGATE_TYPE = "Wallet";

    private final CreditLotRepository creditLotRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.wallet.low-credit-threshold:20}")
    private int lowCreditThreshold;

    @Scheduled(fixedDelayString = "${app.wallet.low-credit-alert-delay-ms:300000}")
    @Transactional
    public void alert() {
        Instant now = Instant.now();
        List<UUID> userIds = creditLotRepository.findUserIdsWithBalanceBelow(lowCreditThreshold, now);

        if (userIds.isEmpty()) {
            log.debug("LowCreditAlertJob: no users below threshold={}", lowCreditThreshold);
            return;
        }

        log.info("LowCreditAlertJob: {} user(s) below threshold={}", userIds.size(), lowCreditThreshold);

        for (UUID userId : userIds) {
            try {
                emitAlertIfNotAlreadySent(userId, now);
            } catch (Exception ex) {
                log.warn("LowCreditAlertJob: error processing userId={}", userId, ex);
            }
        }
    }

    private void emitAlertIfNotAlreadySent(UUID userId, Instant now) {
        // Dedup: store per-user alert key in processed_events. Same key = already alerted this cycle.
        // MVP-simple: permanent dedup (alert once until manually cleared or DB is cleared).
        // T-03-20 accepts residual duplication — this prevents spam at MVP.
        String alertKey = "low-credit-alert:" + userId;
        if (!processedEventRepository.tryInsert(alertKey)) {
            log.debug("LowCreditAlertJob: already alerted userId={} — skipping", userId);
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "userId", userId.toString(),
                    "threshold", lowCreditThreshold,
                    "alertedAt", now.toString()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize LowCreditAlert payload for userId=" + userId, e);
        }

        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(userId.toString())
                .eventType(EVENT_TYPE)
                .payload(payload)
                .build();
        outboxRepository.save(entry);

        log.info("LowCreditAlertJob: emitted LowCreditAlert for userId={}", userId);
    }
}
