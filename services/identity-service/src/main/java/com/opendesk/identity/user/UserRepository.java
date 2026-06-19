package com.opendesk.identity.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
