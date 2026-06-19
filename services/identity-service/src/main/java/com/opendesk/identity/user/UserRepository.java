package com.opendesk.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
