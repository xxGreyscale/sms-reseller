package com.opendesk.contact.suppression;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for per-user suppression lists (CONT-08, D-08).
 *
 * <p>{@link #existsByUserIdAndPhoneE164} is consumed by 04-05 campaign recipient
 * expansion to exclude suppressed numbers before dispatch.
 */
public interface SuppressionRepository extends JpaRepository<SuppressedNumber, UUID> {

    /**
     * Used by campaign expansion (04-05) to check if a number is suppressed for a user.
     * Per-user scoping ensures D-08: suppression by user A does not affect user B.
     */
    boolean existsByUserIdAndPhoneE164(UUID userId, String phoneE164);

    List<SuppressedNumber> findByUserId(UUID userId);
}
