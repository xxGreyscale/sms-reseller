package com.smsreseller.identity;

import com.smsreseller.identity.user.IdentityUserDetailsService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers: IDEN-01 — User aggregate persistence (users table, unique constraints, UserDetailsService).
 *
 * <p>RED phase (plan 02-02): these tests MUST fail until User aggregate, migration, and
 * UserDetailsService are implemented (GREEN phase in same plan).
 *
 * <p>Extends AbstractIntegrationTest which spins up Postgres 16 + Redis 7 via Testcontainers.
 * JPA ddl-auto=create-drop in test profile — no Flyway migration needed for these tests.
 */
class UserPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityUserDetailsService userDetailsService;

    @BeforeEach
    void cleanUsers() {
        // Clean all users before each test to avoid unique constraint violations when
        // multiple test classes share the same Spring context and Postgres instance.
        userRepository.deleteAll();
    }

    @Test
    void newUserHasDefaultStatusPendingVerification() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .phone("+255700000001")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getStatus()).isEqualTo(VerificationStatus.PENDING_VERIFICATION);
    }

    @Test
    void findByEmailReturnsUser() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("bob@example.com")
                .phone("+255700000002")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        User first = User.builder()
                .id(UUID.randomUUID())
                .email("dup@example.com")
                .phone("+255700000003")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.saveAndFlush(first);

        User second = User.builder()
                .id(UUID.randomUUID())
                .email("dup@example.com")
                .phone("+255700000004")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicatePhoneViolatesUniqueConstraint() {
        User first = User.builder()
                .id(UUID.randomUUID())
                .email("carol@example.com")
                .phone("+255700000005")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.saveAndFlush(first);

        User second = User.builder()
                .id(UUID.randomUUID())
                .email("carol2@example.com")
                .phone("+255700000005")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailReturnsTrueWhenUserExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("dave@example.com")
                .phone("+255700000006")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("dave@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("notexists@example.com")).isFalse();
    }

    @Test
    void existsByPhoneReturnsTrueWhenUserExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("eve@example.com")
                .phone("+255700000007")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByPhone("+255700000007")).isTrue();
        assertThat(userRepository.existsByPhone("+255799999999")).isFalse();
    }

    @Test
    void userDetailsServiceLoadsUserByEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("frank@example.com")
                .phone("+255700000008")
                .passwordHash("$2a$10$dummyhashfortest")
                .build();
        userRepository.save(user);

        UserDetails details = userDetailsService.loadUserByUsername("frank@example.com");

        assertThat(details.getUsername()).isEqualTo("frank@example.com");
        assertThat(details.getPassword()).isEqualTo("$2a$10$dummyhashfortest");
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void userDetailsServiceThrowsWhenEmailNotFound() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
