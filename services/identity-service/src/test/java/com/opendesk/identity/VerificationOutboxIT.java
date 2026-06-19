package com.opendesk.identity;

import com.opendesk.identity.outbox.OutboxEntry;
import com.opendesk.identity.outbox.OutboxRepository;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.user.VerificationStatus;
import com.opendesk.identity.verification.VerificationFinalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: IDEN-03 — 50 free SMS credits granted via transactional outbox on NIDA verification.
 *
 * <p>Verifies VerificationFinalizerImpl:
 * <ul>
 *   <li>Flips user status to VERIFIED</li>
 *   <li>Writes exactly one outbox row (event_type "UserVerified", freeCredits=50) in the same TX</li>
 *   <li>Idempotent — re-running finalize on an already-VERIFIED user does not add a second row</li>
 * </ul>
 */
class VerificationOutboxIT extends AbstractIntegrationTest {

    @Autowired
    private VerificationFinalizer verificationFinalizer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void writesOutboxRowInSameTransactionAsVerifiedFlip() {
        // Given: a PENDING user
        UUID userId = UUID.randomUUID();
        userRepository.save(User.builder()
                .id(userId)
                .email("outbox-" + userId + "@test.com")
                .phone("+255702" + System.nanoTime() % 1_000_000)
                .passwordHash("{bcrypt}irrelevant")
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build());

        // When: finalize is called
        verificationFinalizer.finalizeVerification(userId);

        // Then: status is VERIFIED
        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // And: exactly one outbox row for this user
        List<OutboxEntry> entries = outboxRepository.findBySentFalse();
        List<OutboxEntry> forThisUser = entries.stream()
                .filter(e -> userId.toString().equals(e.getAggregateId()))
                .toList();
        assertThat(forThisUser).hasSize(1);
        OutboxEntry entry = forThisUser.get(0);
        assertThat(entry.getEventType()).isEqualTo("UserVerified");
        assertThat(entry.getPayload()).contains("50");
        assertThat(entry.isSent()).isFalse();
    }

    @Test
    void finalizeIsIdempotentForAlreadyVerifiedUser() {
        // Given: a PENDING user
        UUID userId = UUID.randomUUID();
        userRepository.save(User.builder()
                .id(userId)
                .email("outbox-idem-" + userId + "@test.com")
                .phone("+255703" + System.nanoTime() % 1_000_000)
                .passwordHash("{bcrypt}irrelevant")
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build());

        // When: finalize is called twice
        verificationFinalizer.finalizeVerification(userId);
        verificationFinalizer.finalizeVerification(userId);

        // Then: still exactly one outbox row for this user (idempotent guard)
        List<OutboxEntry> allForUser = outboxRepository.findAll().stream()
                .filter(e -> userId.toString().equals(e.getAggregateId()))
                .toList();
        assertThat(allForUser).hasSize(1);
    }
}
