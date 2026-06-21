package com.opendesk.messaging.senderid;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SenderIdRequest} entities.
 */
public interface SenderIdRepository extends JpaRepository<SenderIdRequest, UUID> {

    /** IDOR-safe lookup — returns empty if id belongs to another user. */
    Optional<SenderIdRequest> findByIdAndUserId(UUID id, UUID userId);

    /** All sender-ID requests for a user (own listing). */
    List<SenderIdRequest> findByUserId(UUID userId);
}
