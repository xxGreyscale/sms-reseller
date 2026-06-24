package com.smsreseller.identity.sender;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SenderId} entities.
 */
public interface SenderIdRepository extends JpaRepository<SenderId, UUID> {

    /** Find a user's sender ID, if already assigned. */
    Optional<SenderId> findByUserId(UUID userId);

    /** Check if a given numeric shortcode is already taken (for collision detection). */
    boolean existsBySenderId(String senderId);
}
