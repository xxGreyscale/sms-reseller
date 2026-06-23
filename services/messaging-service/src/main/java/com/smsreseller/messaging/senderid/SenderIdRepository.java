package com.smsreseller.messaging.senderid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Admin approval queue, filtered by status, newest-first (ADMN-04). */
    Page<SenderIdRequest> findByStatusOrderByCreatedAtDesc(SenderIdStatus status, Pageable pageable);

    /** Admin approval queue, all statuses, newest-first (ADMN-04). */
    Page<SenderIdRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
