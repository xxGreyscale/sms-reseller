package com.smsreseller.identity.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.identity.outbox.OutboxEntry;
import com.smsreseller.identity.outbox.OutboxRepository;
import com.smsreseller.identity.outbox.UserVerifiedEvent;
import com.smsreseller.identity.sender.SenderIdService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional implementation of {@link VerificationFinalizer} (IDEN-03, SNDR-01).
 *
 * <p>This is the committed TX home class — all three writes happen atomically:
 * <ol>
 *   <li>Flip {@code user.status = VERIFIED}</li>
 *   <li>INSERT a default 6-digit numeric sender ID row (SNDR-01)</li>
 *   <li>INSERT an outbox row with a {@code UserVerified} event carrying 50 free credits (IDEN-03)</li>
 * </ol>
 *
 * <p>Idempotent: if the user is already {@code VERIFIED}, the method returns immediately
 * without inserting a second sender ID or outbox row (T-02-03: double credit grant prevention).
 *
 * <p>Phase 3 boundary: the credit grant itself (wallet ledger update) is NOT done here.
 * The outbox row is the at-least-once delivery channel; Phase 3 wallet service consumes
 * {@code UserVerified} events and deduplicates by {@code eventId} (Pitfall 5).
 *
 * <p>Bean name {@code "transactionalVerificationFinalizer"} displaces the no-op placeholder
 * ({@link NoOpVerificationFinalizer}) which is conditional on this name being absent.
 */
@Service("transactionalVerificationFinalizer")
@RequiredArgsConstructor
@Slf4j
public class VerificationFinalizerImpl implements VerificationFinalizer {

    private final UserRepository userRepository;
    private final SenderIdService senderIdService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Finalize a successful NIDA verification.
     *
     * <p>Executes all three writes (status flip, sender ID, outbox) in a single Postgres
     * transaction. If the transaction rolls back for any reason, none of the three writes
     * will be committed (Pattern 4 transactional outbox).
     *
     * @param userId UUID of the user whose identity was successfully verified
     * @throws IllegalArgumentException if no user with the given ID exists
     */
    @Override
    @Transactional
    public void finalizeVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Idempotent guard — prevents double sender ID and double credit grant (T-02-03)
        if (user.getStatus() == VerificationStatus.VERIFIED) {
            log.info("finalizeVerification: userId={} is already VERIFIED — skipping (idempotent)", userId);
            return;
        }

        // 1. Flip status to VERIFIED
        user.setStatus(VerificationStatus.VERIFIED);
        userRepository.save(user);

        // 2. Assign default numeric sender ID (SNDR-01) — idempotent within SenderIdService
        String senderId = senderIdService.assign(userId);
        log.info("finalizeVerification: userId={} assigned senderId={} (SNDR-01)", userId, senderId);

        // 3. Write UserVerified outbox row (IDEN-03) — same TX as steps 1 and 2
        UUID eventId = UUID.randomUUID();
        UserVerifiedEvent event = new UserVerifiedEvent(eventId, userId, UserVerifiedEvent.DEFAULT_FREE_CREDITS);
        String payload = toJson(event);

        OutboxEntry outboxEntry = OutboxEntry.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .aggregateType("User")
                .aggregateId(userId.toString())
                .eventType("UserVerified")
                .payload(payload)
                .build();
        outboxRepository.save(outboxEntry);

        log.info("finalizeVerification: userId={} → VERIFIED, senderId={}, outboxEventId={} (IDEN-03)",
                userId, senderId, eventId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + obj, e);
        }
    }
}
