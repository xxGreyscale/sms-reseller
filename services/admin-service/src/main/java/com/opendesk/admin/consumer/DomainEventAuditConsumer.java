package com.opendesk.admin.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.admin.audit.AuditService;
import com.opendesk.admin.idempotency.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passive idempotent domain-event audit consumer (D-09b).
 *
 * <p>Binds to key platform events across identity.events, wallet.events, and messaging.events
 * WITHOUT declaring those exchanges (admin-service owns none of them). Each {@code @Exchange}
 * annotation uses {@code ignoreDeclarationExceptions="true"} so that startup never fails
 * if an exchange doesn't exist yet (T-05-20, RESEARCH.md Pitfall 1).
 *
 * <p>For every event received, the handler:
 * <ol>
 *   <li>Calls {@link ProcessedEventRepository#tryInsert} — if already processed, returns (T-05-18)</li>
 *   <li>Calls {@link AuditService#recordMutation} with {@code actor="system"} to append
 *       an audit row (D-09b)</li>
 * </ol>
 *
 * <p>Covered events (per D-09 "key platform actions"):
 * <ul>
 *   <li>{@code identity.UserVerified} — user passed NIDA KYC</li>
 *   <li>{@code messaging.SenderIdDecided} — sender ID approved or rejected</li>
 *   <li>{@code wallet.PaymentConfirmed} — bundle payment confirmed</li>
 *   <li>{@code wallet.RefundGranted} — admin manual refund issued</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventAuditConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ── identity.events / identity.UserVerified ──────────────────────────────

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "admin.identity.UserVerified", durable = "true"),
            exchange = @Exchange(
                    name = "identity.events",
                    type = "topic",
                    durable = "true",
                    ignoreDeclarationExceptions = "true"
            ),
            key = "identity.UserVerified"
    ))
    @Transactional
    public void onUserVerified(Message message) {
        handleEvent(message, "UserVerified", "identity.UserVerified");
    }

    // ── messaging.events / messaging.SenderIdDecided ─────────────────────────

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "admin.messaging.SenderIdDecided", durable = "true"),
            exchange = @Exchange(
                    name = "messaging.events",
                    type = "topic",
                    durable = "true",
                    ignoreDeclarationExceptions = "true"
            ),
            key = "messaging.SenderIdDecided"
    ))
    @Transactional
    public void onSenderIdDecided(Message message) {
        handleEvent(message, "SenderIdDecided", "messaging.SenderIdDecided");
    }

    // ── wallet.events / wallet.PaymentConfirmed ──────────────────────────────

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "admin.wallet.PaymentConfirmed", durable = "true"),
            exchange = @Exchange(
                    name = "wallet.events",
                    type = "topic",
                    durable = "true",
                    ignoreDeclarationExceptions = "true"
            ),
            key = "wallet.PaymentConfirmed"
    ))
    @Transactional
    public void onPaymentConfirmed(Message message) {
        handleEvent(message, "PaymentConfirmed", "wallet.PaymentConfirmed");
    }

    // ── wallet.events / wallet.RefundGranted ─────────────────────────────────

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "admin.wallet.RefundGranted", durable = "true"),
            exchange = @Exchange(
                    name = "wallet.events",
                    type = "topic",
                    durable = "true",
                    ignoreDeclarationExceptions = "true"
            ),
            key = "wallet.RefundGranted"
    ))
    @Transactional
    public void onRefundGranted(Message message) {
        handleEvent(message, "RefundGranted", "wallet.RefundGranted");
    }

    // ── shared handler ───────────────────────────────────────────────────────

    /**
     * Shared idempotency + audit-write logic for all event types.
     *
     * @param message   raw AMQP message (body is JSON)
     * @param eventType short event type label (used as audit action)
     * @param routingKey AMQP routing key (for logging)
     */
    private void handleEvent(Message message, String eventType, String routingKey) {
        String eventId;
        String aggregateId;
        String payloadJson;

        try {
            payloadJson = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload = objectMapper.readValue(payloadJson, java.util.Map.class);
            eventId = extractEventId(payload, routingKey);
            aggregateId = extractAggregateId(payload, routingKey);
        } catch (Exception e) {
            log.warn("Failed to parse {} message — skipping audit write: {}", routingKey, e.getMessage());
            return;
        }

        if (!processedEventRepository.tryInsert(eventId)) {
            log.info("Duplicate {} event ignored: eventId={}", eventType, eventId);
            return;
        }

        auditService.recordMutation("system", eventType, aggregateId, payloadJson);
        log.info("Audit row written for {} event: eventId={} target={}", eventType, eventId, aggregateId);
    }

    /**
     * Extracts the event id from the payload. Checks common field names in order.
     */
    private String extractEventId(java.util.Map<String, Object> payload, String routingKey) {
        // All services use eventId as the idempotency key (D-09)
        Object id = payload.get("eventId");
        if (id == null) {
            id = payload.get("event_id");
        }
        if (id == null) {
            // Fall back to a content-hash so consumers can at least track the message
            id = routingKey + ":" + payload.toString().hashCode();
            log.warn("Event payload missing eventId field for {} — using fallback id={}", routingKey, id);
        }
        return id.toString();
    }

    /**
     * Extracts the aggregate id (the "who" or "what" this event is about).
     */
    private String extractAggregateId(java.util.Map<String, Object> payload, String routingKey) {
        // Try common aggregate id field names across all services
        for (String key : java.util.List.of("userId", "senderId", "paymentId", "refundId", "aggregateId", "id")) {
            Object val = payload.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }
}
