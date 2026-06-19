package com.opendesk.identity;

import com.opendesk.identity.sender.SenderIdService;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.user.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: SNDR-01 — Default numeric sender ID assigned at NIDA verification (D-03).
 *
 * <p>Verifies SenderIdService.assign(userId):
 * <ul>
 *   <li>Generates a unique 6-digit zero-padded numeric shortcode</li>
 *   <li>Idempotent — calling assign twice for the same userId returns the same shortcode</li>
 * </ul>
 */
class SenderIdIT extends AbstractIntegrationTest {

    @Autowired
    private SenderIdService senderIdService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void assignsDefaultNumericSenderIdOnVerification() {
        // Given: a persisted user
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("sndr-" + UUID.randomUUID() + "@test.com")
                .phone("+255700" + System.nanoTime() % 1_000_000)
                .passwordHash("{bcrypt}irrelevant")
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build());

        // When: sender ID is assigned
        String senderId = senderIdService.assign(user.getId());

        // Then: 6-digit numeric, zero-padded
        assertThat(senderId).matches("^\\d{6}$");
    }

    @Test
    void assignIsIdempotent() {
        // Given: a persisted user
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("sndr-idempotent-" + UUID.randomUUID() + "@test.com")
                .phone("+255701" + System.nanoTime() % 1_000_000)
                .passwordHash("{bcrypt}irrelevant")
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build());

        // When: assign is called twice
        String first = senderIdService.assign(user.getId());
        String second = senderIdService.assign(user.getId());

        // Then: same shortcode returned — no duplicate row
        assertThat(first).isEqualTo(second);
    }
}
