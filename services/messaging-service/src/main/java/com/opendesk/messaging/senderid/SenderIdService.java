package com.opendesk.messaging.senderid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendesk.messaging.outbox.OutboxEntry;
import com.opendesk.messaging.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sender-ID request service — state machine REQUESTED → APPROVED | REJECTED (SNDR-02/03/04).
 *
 * <p>On every admin decision (approve/reject), a {@code SenderIdDecided} outbox row is written
 * atomically in the same transaction. The {@link com.opendesk.messaging.outbox.OutboxRelay}
 * picks it up and publishes it to {@code messaging.events} with routing key
 * {@code messaging.SenderIdDecided} (Pattern 4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SenderIdService {

    private final SenderIdRepository senderIdRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Submit a new sender-ID request (SNDR-02).
     * Persisted in REQUESTED status, scoped to the JWT subject.
     */
    @Transactional
    public SenderIdRequest request(UUID userId, String senderName) {
        SenderIdRequest req = SenderIdRequest.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .senderName(senderName)
                .status(SenderIdStatus.REQUESTED)
                .build();

        req = senderIdRepository.save(req);
        log.info("SenderIdRequest created: id={} userId={} senderName={}", req.getId(), userId, senderName);
        return req;
    }

    /**
     * List all sender-ID requests for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<SenderIdRequest> listForUser(UUID userId) {
        return senderIdRepository.findByUserId(userId);
    }

    /**
     * Approve a sender-ID request (SNDR-03, admin-only).
     * Transitions REQUESTED → APPROVED and writes a {@code SenderIdDecided} outbox event.
     *
     * @throws IllegalStateException if not in REQUESTED state
     */
    @Transactional
    public SenderIdRequest approve(UUID requestId) {
        SenderIdRequest req = senderIdRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("SenderIdRequest not found: " + requestId));

        if (req.getStatus() != SenderIdStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Cannot approve sender-ID request in state " + req.getStatus());
        }

        req.setStatus(SenderIdStatus.APPROVED);
        req.setDecidedAt(Instant.now());
        req = senderIdRepository.save(req);

        writeDecidedEvent(req, "APPROVED", null);
        log.info("SenderIdRequest approved: id={} userId={}", requestId, req.getUserId());
        return req;
    }

    /**
     * Reject a sender-ID request (SNDR-03, admin-only).
     * Transitions REQUESTED → REJECTED and writes a {@code SenderIdDecided} outbox event.
     *
     * @throws IllegalStateException if not in REQUESTED state
     */
    @Transactional
    public SenderIdRequest reject(UUID requestId, String reason) {
        SenderIdRequest req = senderIdRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("SenderIdRequest not found: " + requestId));

        if (req.getStatus() != SenderIdStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Cannot reject sender-ID request in state " + req.getStatus());
        }

        req.setStatus(SenderIdStatus.REJECTED);
        req.setRejectReason(reason);
        req.setDecidedAt(Instant.now());
        req = senderIdRepository.save(req);

        writeDecidedEvent(req, "REJECTED", reason);
        log.info("SenderIdRequest rejected: id={} userId={} reason={}", requestId, req.getUserId(), reason);
        return req;
    }

    private void writeDecidedEvent(SenderIdRequest req, String decision, String reason) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("requestId", req.getId().toString());
            payload.put("userId", req.getUserId().toString());
            payload.put("senderName", req.getSenderName());
            payload.put("decision", decision);
            if (reason != null) {
                payload.put("reason", reason);
            }

            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEntry entry = OutboxEntry.builder()
                    .id(UUID.randomUUID())
                    .eventId(UUID.randomUUID())
                    .aggregateType("SenderIdRequest")
                    .aggregateId(req.getId().toString())
                    .eventType("SenderIdDecided")
                    .payload(payloadJson)
                    .build();

            outboxRepository.save(entry);
            log.debug("SenderIdDecided outbox entry written for requestId={} decision={}", req.getId(), decision);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SenderIdDecided payload", e);
        }
    }
}
