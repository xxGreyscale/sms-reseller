package com.smsreseller.messaging.sms;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Stub SMS provider for dev and test profiles (D-12).
 *
 * <p>Outcome routing by magic phone suffix (mirrors StubPaymentGateway pattern):
 * <ul>
 *   <li>Last 4 digits == "0001" → HARD_FAIL (permanent failure — invalid number)</li>
 *   <li>Last 4 digits == "0002" → TRANSIENT_FAIL (temporary failure — provider overload)</li>
 *   <li>All other numbers → ACCEPTED (configurable via {@code app.sms.stub.default-outcome})</li>
 * </ul>
 *
 * <p>Delivery receipt simulation (Pitfall 6 coverage):
 * After a configurable delay ({@code app.sms.stub.dlr-delay-ms}; default 10 000ms; override
 * to 100ms in application-test.yml), the DLR sweep fires a delivery callback via a settable
 * {@link #setDeliveryReceiptHandler handler}. Plan 04-06 attaches a real handler; until then
 * the handler is a no-op.
 *
 * <p>Note: {@code StubSmsProvider} can be constructed directly in unit tests (no Spring context).
 * Fields annotated with {@code @Value} have sensible defaults so they remain unset in plain unit
 * tests. The {@code @Scheduled} DLR sweep only fires in a Spring context.
 */
@Profile("stub")
@Service
@Slf4j
public class StubSmsProvider implements SmsProvider {

    private static final String HARD_FAIL_SUFFIX      = "0001";
    private static final String TRANSIENT_FAIL_SUFFIX = "0002";

    /** Default outcome for phone numbers that don't match a magic suffix. */
    @Value("${app.sms.stub.default-outcome:ACCEPTED}")
    private String defaultOutcome = "ACCEPTED";  // field default for unit test construction

    /** Delay before stub fires a delivery receipt callback. */
    @Value("${app.sms.stub.dlr-delay-ms:10000}")
    private long dlrDelayMs = 10_000L;  // field default for unit test construction

    /**
     * Map of accepted message external IDs → accept timestamp.
     * DLR sweep checks entries past dlrDelayMs and invokes the handler.
     */
    private final Map<String, Instant> pendingDeliveries = new ConcurrentHashMap<>();

    /**
     * Seam for 04-06 to attach a real delivery-receipt handler.
     * Contract: {@code (externalId, DELIVERED|FAILED)} — matches real DLR webhook shape.
     */
    @Setter
    private BiConsumer<String, String> deliveryReceiptHandler = (id, status) ->
            log.debug("StubSmsProvider: DLR fired externalId={} status={} (no handler wired yet)", id, status);

    @Override
    public SmsResult send(String phoneE164, String body, String senderId) {
        String outcome = resolveOutcome(phoneE164);
        log.debug("StubSmsProvider.send: phone={} outcome={}", phoneE164, outcome);

        return switch (outcome) {
            case "HARD_FAIL" -> SmsResult.hardFail("stub:hard-fail for " + phoneE164);
            case "TRANSIENT_FAIL" -> SmsResult.transientFail("stub:transient-fail for " + phoneE164);
            default -> {
                String externalId = UUID.randomUUID().toString();
                pendingDeliveries.put(externalId, Instant.now());
                yield SmsResult.accepted(externalId);
            }
        };
    }

    /**
     * DLR simulation sweep — fires after {@code dlrDelayMs}.
     * Only active when running in a Spring context (requires @EnableScheduling).
     */
    @Scheduled(fixedDelay = 5_000)
    public void simulateDeliveryReceipts() {
        Instant cutoff = Instant.now().minusMillis(dlrDelayMs);
        pendingDeliveries.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                log.debug("StubSmsProvider: DLR sweep firing for externalId={}", entry.getKey());
                deliveryReceiptHandler.accept(entry.getKey(), "DELIVERED");
                return true;
            }
            return false;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String resolveOutcome(String phoneE164) {
        if (phoneE164 != null) {
            if (phoneE164.endsWith(HARD_FAIL_SUFFIX))      return "HARD_FAIL";
            if (phoneE164.endsWith(TRANSIENT_FAIL_SUFFIX)) return "TRANSIENT_FAIL";
        }
        return defaultOutcome;
    }
}
