package com.opendesk.payment.callback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.payment.outbox.OutboxEntry;
import com.opendesk.payment.outbox.OutboxRepository;
import com.opendesk.payment.outbox.PaymentConfirmedEvent;
import com.opendesk.payment.payment.Payment;
import com.opendesk.payment.payment.PaymentRepository;
import com.opendesk.payment.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Processes Azampay webhook callbacks idempotently.
 *
 * <p>Implements PYMT-04/06, D-04, Pitfall 5, T-03-11, T-03-12, T-03-14:
 * <ul>
 *   <li>Idempotent guard: if payment is already SUCCESS, return immediately (no double-emit)</li>
 *   <li>Handles BOTH PENDING and EXPIRED inbound states (D-04, Pitfall 5) — EXPIRED may have
 *       timed out before Azampay delivered the callback; money left the customer's account, honor it</li>
 *   <li>Success path: flip to SUCCESS + set operatorReference + write PaymentConfirmedEvent outbox row
 *       in ONE transaction (T-03-14 transactional outbox)</li>
 *   <li>Fail path: flip to FAILED — no outbox row</li>
 *   <li>Outbox {@code event_id} UNIQUE constraint (V4 migration) provides a second layer of
 *       idempotency guard against concurrent duplicate callbacks (T-03-11)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackProcessor {

    private static final String EVENT_TYPE = "PaymentConfirmed";
    private static final String AGGREGATE_TYPE = "Payment";

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Processes an Azampay callback for the given payment external ID.
     *
     * <p>All state transitions and outbox writes are committed in a single transaction.
     *
     * @param payload the Azampay callback payload (utilityRef = our payment UUID)
     */
    @Transactional
    public void processCallback(AzampayCallbackPayload payload) {
        String externalId = payload.utilityRef();
        if (externalId == null || externalId.isBlank()) {
            log.warn("CallbackProcessor: received callback with blank utilityRef — ignoring");
            return;
        }

        Payment payment = paymentRepository.findByExternalId(externalId).orElse(null);
        if (payment == null) {
            // T-03-12: utilityRef does not match any payment — likely spoofed; ignore
            log.warn("CallbackProcessor: no payment found for externalId={} — ignoring (T-03-12)", externalId);
            return;
        }

        // Idempotent guard: skip if already SUCCESS (PYMT-06)
        // CRITICAL: do NOT skip on EXPIRED — EXPIRED → SUCCESS is the late-success path (D-04, Pitfall 5)
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.debug("CallbackProcessor: payment already SUCCESS paymentId={} — skipping (idempotent)",
                    payment.getId());
            return;
        }

        String status = payload.transactionStatus();
        if ("success".equalsIgnoreCase(status)) {
            handleSuccess(payment, payload);
        } else {
            handleFail(payment);
        }
    }

    private void handleSuccess(Payment payment, AzampayCallbackPayload payload) {
        // Flip to SUCCESS (handles both PENDING and EXPIRED inbound — D-04, Pitfall 5)
        PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setOperatorReference(payload.reference());
        paymentRepository.save(payment);

        // Write PaymentConfirmedEvent outbox row in the same transaction (T-03-14)
        UUID eventId = UUID.randomUUID();
        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                eventId.toString(),
                payment.getUserId(),
                payment.getId(),
                payment.getSmsCount()
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize PaymentConfirmedEvent", e);
        }

        OutboxEntry outboxEntry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)  // UNIQUE — second concurrent callback hits UK violation
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(payment.getId().toString())
                .eventType(EVENT_TYPE)
                .payload(payloadJson)
                .build();

        outboxRepository.save(outboxEntry);

        log.info("CallbackProcessor: payment {} → SUCCESS (was {}) paymentId={} userId={} smsCount={}",
                previousStatus, PaymentStatus.SUCCESS, payment.getId(), payment.getUserId(), payment.getSmsCount());
    }

    private void handleFail(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
        log.info("CallbackProcessor: payment PENDING/EXPIRED → FAILED paymentId={}", payment.getId());
    }
}
