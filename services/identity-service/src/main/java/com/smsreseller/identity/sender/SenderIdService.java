package com.smsreseller.identity.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Assigns platform-internal numeric sender IDs to users (SNDR-01).
 *
 * <p>The sender ID is a 6-digit zero-padded numeric shortcode (e.g. "042137").
 * It is a placeholder until TCRA provisions real alphanumeric sender IDs (SNDR-02, Phase 4).
 *
 * <p>Uniqueness is enforced at two levels:
 * <ol>
 *   <li>Collision-checked in memory via {@link SenderIdRepository#existsBySenderId} before insert</li>
 *   <li>Unique constraint on the {@code sender_id} column in Postgres — final safety net</li>
 * </ol>
 *
 * <p>{@link #assign(UUID)} is idempotent: if a sender ID already exists for the given user,
 * the existing shortcode is returned without creating a duplicate row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SenderIdService {

    private static final int MAX_RETRIES = 10;
    private static final RandomGenerator RNG = RandomGenerator.getDefault();

    private final SenderIdRepository senderIdRepository;

    /**
     * Assign a unique 6-digit numeric sender ID to the given user.
     *
     * <p>If a sender ID is already assigned for this user, the existing shortcode is
     * returned immediately (idempotent). Otherwise, a new unique shortcode is generated
     * (with collision-retry), persisted, and returned.
     *
     * @param userId the UUID of the user to assign
     * @return the 6-digit numeric sender ID string (e.g. "042137")
     * @throws IllegalStateException if MAX_RETRIES collisions occur (extremely unlikely)
     */
    @Transactional
    public String assign(UUID userId) {
        // Idempotent: return existing sender ID if already assigned
        return senderIdRepository.findByUserId(userId)
                .map(SenderId::getSenderId)
                .orElseGet(() -> generate(userId));
    }

    private String generate(UUID userId) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String candidate = String.format("%06d", RNG.nextInt(1_000_000));
            if (!senderIdRepository.existsBySenderId(candidate)) {
                SenderId entity = SenderId.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .senderId(candidate)
                        .build();
                senderIdRepository.save(entity);
                log.info("Assigned sender ID {} to userId={} (placeholder until TCRA, SNDR-01)",
                        candidate, userId);
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique sender ID after " + MAX_RETRIES + " attempts for userId=" + userId);
    }
}
