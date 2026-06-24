package com.smsreseller.contact.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ContactGroup and membership queries.
 *
 * <p>findContactPhonesByGroupIdsAndUserId is consumed by 04-05 campaign
 * recipient expansion to fan out phone numbers for a campaign.
 */
public interface ContactGroupRepository extends JpaRepository<ContactGroup, UUID> {

    Optional<ContactGroup> findByIdAndUserId(UUID id, UUID userId);

    Page<ContactGroup> findByUserId(UUID userId, Pageable pageable);

    /**
     * Returns distinct E.164 phone numbers for all contacts in the given group IDs,
     * scoped to userId. Consumed by 04-05 campaign expansion.
     */
    @Query(value = """
            SELECT DISTINCT c.phone_e164
              FROM contact_group_members m
              JOIN contacts c ON c.id = m.contact_id
              JOIN contact_groups g ON g.id = m.group_id
             WHERE m.group_id IN (:groupIds)
               AND g.user_id = :userId
            """, nativeQuery = true)
    List<String> findContactPhonesByGroupIdsAndUserId(
            @Param("groupIds") Collection<UUID> groupIds,
            @Param("userId") UUID userId);
}
