package com.opendesk.messaging.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Campaign} entities.
 *
 * <p>All user-scoped queries include {@code userId} to prevent IDOR (Pitfall 7).
 */
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    /** Find campaign by ID scoped to a specific user. Returns empty if id belongs to another user. */
    Optional<Campaign> findByIdAndUserId(UUID id, UUID userId);

    /** Paginated campaign history for a user (MESG-06). */
    Page<Campaign> findByUserId(UUID userId, Pageable pageable);

    /**
     * Scheduled campaign dispatcher query (D-10 / Pattern 8).
     * Returns SCHEDULED campaigns whose {@code scheduledAt} is before {@code cutoff}.
     */
    List<Campaign> findByStatusAndScheduledAtBefore(CampaignStatus status, Instant cutoff, Pageable pageable);
}
