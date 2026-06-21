package com.opendesk.contact.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Contact entities.
 *
 * <p>All queries include userId to prevent IDOR (T-04-01).
 * existsByUserIdAndPhoneE164 is consumed by CSV import (04-03) for deduplication.
 */
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /** IDOR-safe single-record fetch: returns empty if id exists but belongs to another user. */
    Optional<Contact> findByIdAndUserId(UUID id, UUID userId);

    /** Paginated list scoped to the authenticated user. */
    Page<Contact> findByUserId(UUID userId, Pageable pageable);

    /** Uniqueness check (also used by CSV import in 04-03). */
    boolean existsByUserIdAndPhoneE164(UUID userId, String phoneE164);

    /**
     * Idempotent insert used by CSV import (04-03).
     * Returns 1 if inserted, 0 if duplicate (ON CONFLICT DO NOTHING).
     */
    @Modifying
    @Query(value = """
            INSERT INTO contacts (id, user_id, name, phone_e164, created_at, updated_at)
            VALUES (:id, :userId, :name, :phoneE164, now(), now())
            ON CONFLICT (user_id, phone_e164) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("name") String name,
            @Param("phoneE164") String phoneE164);
}
