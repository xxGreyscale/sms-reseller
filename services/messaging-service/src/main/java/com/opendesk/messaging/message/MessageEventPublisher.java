package com.opendesk.messaging.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.messaging.outbox.OutboxEntry;
import com.opendesk.messaging.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Writes transactional outbox entries for messaging credit events.
 *
 * <p>Events emitted here are picked up by {@link com.opendesk.messaging.outbox.OutboxRelay}
 * and published to {@code messaging.events}. Wallet-service's MessagingEventConsumer (04-07)
 * binds to these routing keys and applies CONSUME/RELEASE/REFUND against the credit ledger.
 *
 * <p>Every event carries a unique {@code eventId} — the wallet-side idempotency guard uses it
 * to detect and discard duplicate deliveries (T-04-08).
 *
 * <p>D-04 / T-04-13: this component MUST be called from within the same transaction as the
 * domain state change (e.g. message.setStatus(SENT)). It must NOT call wallet-service
 * synchronously — write the outbox entry only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // ── Event routing key suffixes (must match wallet MessagingEventConsumer bindings) ──

    public static final String EVENT_ACCEPTED  = "MessageAccepted";
    public static final String EVENT_RELEASED  = "MessageReleased";
    public static final String EVENT_REFUND    = "MessageRefundDue";

    /**
     * Write a MessageAccepted outbox entry.
     * Wallet-service will CONSUME one credit from {@code lotId} when this is relayed.
     */
    public void accepted(UUID messageId, UUID userId, UUID lotId) {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "messageId", messageId.toString(),
                "userId", userId.toString(),
                "lotId", lotId.toString()
        );
        writeOutbox(messageId, EVENT_ACCEPTED, payload);
        log.debug("MessageAccepted outbox written: messageId={} lotId={}", messageId, lotId);
    }

    /**
     * Write a MessageReleased outbox entry.
     * Wallet-service will RELEASE one reserved credit from {@code lotId} when relayed
     * (decrement reserved without consuming — MESG-09 reserved-but-never-sent path).
     */
    public void released(UUID messageId, UUID userId, UUID lotId) {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "messageId", messageId.toString(),
                "userId", userId.toString(),
                "lotId", lotId.toString()
        );
        writeOutbox(messageId, EVENT_RELEASED, payload);
        log.debug("MessageReleased outbox written: messageId={} lotId={}", messageId, lotId);
    }

    /**
     * Write a MessageRefundDue outbox entry.
     * Wallet-service will call creditBack(userId, creditsToRefund, messageId) — used by
     * DeadLetterConsumer (04-06) for messages that exhausted all retries.
     */
    public void refundDue(UUID messageId, UUID userId, UUID lotId, int creditsToRefund) {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "messageId", messageId.toString(),
                "userId", userId.toString(),
                "lotId", lotId.toString(),
                "creditsToRefund", creditsToRefund
        );
        writeOutbox(messageId, EVENT_REFUND, payload);
        log.debug("MessageRefundDue outbox written: messageId={} lotId={} credits={}", messageId, lotId, creditsToRefund);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void writeOutbox(UUID messageId, String eventType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType + " payload for messageId=" + messageId, e);
        }

        // eventId is embedded in the payload; use it as the outbox row's eventId for relay deduplication
        String eventIdStr = payload.get("eventId").toString();

        OutboxEntry entry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.fromString(eventIdStr))
                .aggregateType("OutboundMessage")
                .aggregateId(messageId.toString())
                .eventType(eventType)
                .payload(payloadJson)
                .sent(false)
                .build();

        outboxRepository.save(entry);
    }
}
