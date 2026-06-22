package com.opendesk.identity.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link User} aggregate.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link IdentityUserDetailsService} — loadUserByUsername(email)</li>
 *   <li>Registration service (02-03) — existsByEmail, existsByPhone before insert</li>
 *   <li>Login controller (02-03) — findByEmail for credential check</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Find a user by their email address (the login identifier, D-10). */
    Optional<User> findByEmail(String email);

    /** Check if an email address is already registered (for duplicate detection at registration). */
    boolean existsByEmail(String email);

    /** Check if a phone number is already registered (for duplicate detection at registration). */
    boolean existsByPhone(String phone);

    /**
     * Find users with the given status whose account was created before the given cutoff.
     * Used by {@link com.opendesk.identity.verification.VerificationRetryJob} to find
     * PENDING users eligible for verification retry (IDEN-08).
     *
     * @param status  verification status to filter by
     * @param cutoff  only users created before this instant are returned
     * @param pageable pagination — use PageRequest.of(0, N) to bound the result
     * @return list of matching users, bounded by pageable
     */
    List<User> findByStatusAndCreatedAtBefore(VerificationStatus status, Instant cutoff, Pageable pageable);

    /**
     * Case-insensitive substring search by email OR phone — powers the admin user search (ADMN-02).
     * When {@code q} is null or blank, returns all users.
     *
     * @param q        search term (email or phone substring); null/blank → all users
     * @param pageable pagination
     * @return page of matching users
     */
    @Query("SELECT u FROM User u WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR u.phone LIKE CONCAT('%', :q, '%')")
    Page<User> searchByEmailOrPhone(@Param("q") String q, Pageable pageable);
}
