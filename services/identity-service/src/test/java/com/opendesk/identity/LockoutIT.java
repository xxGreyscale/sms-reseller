package com.opendesk.identity;

import com.opendesk.identity.lockout.LoginAttemptService;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: IDEN-04 (lockout path) — Account lockout after repeated failed login attempts (D-12).
 *
 * <p>Tests Redis-backed lockout: 5 failures → 423, success resets counter.
 */
class LockoutIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private static final String EMAIL = "lockout-test@example.com";
    private static final String PASSWORD = "S3cur3Pass!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .phone("+255700000002")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build();
        userRepository.save(user);
        // reset lockout counter before each test
        loginAttemptService.reset(EMAIL);
    }

    @Test
    void locksAccountAfterMaxFailedAttempts() {
        var badRequest = Map.of("email", EMAIL, "password", "wrong!", "deviceId", "dev-lock");

        // 5 failures
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> r = restTemplate.postForEntity("/auth/login", badRequest, String.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt → locked (423)
        ResponseEntity<String> locked = restTemplate.postForEntity("/auth/login", badRequest, String.class);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    }

    @Test
    void successfulLoginResetsLockoutCounter() {
        var badRequest = Map.of("email", EMAIL, "password", "wrong!", "deviceId", "dev-lock");
        var goodRequest = Map.of("email", EMAIL, "password", PASSWORD, "deviceId", "dev-lock");

        // 4 failures
        for (int i = 0; i < 4; i++) {
            restTemplate.postForEntity("/auth/login", badRequest, String.class);
        }

        // success — resets counter
        ResponseEntity<String> ok = restTemplate.postForEntity("/auth/login", goodRequest, String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5 more failures should lock again (counter was reset)
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity("/auth/login", badRequest, String.class);
        }
        ResponseEntity<String> locked = restTemplate.postForEntity("/auth/login", badRequest, String.class);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    }

    @Test
    void lockoutIsCheckedBeforeAuthAttempt() {
        // Force lock via direct service call (simulates state from prior session)
        for (int i = 0; i < 5; i++) {
            loginAttemptService.increment(EMAIL);
        }
        assertThat(loginAttemptService.isLocked(EMAIL)).isTrue();

        // Even with correct credentials, should get 423
        var goodRequest = Map.of("email", EMAIL, "password", PASSWORD, "deviceId", "dev-lock");
        ResponseEntity<String> r = restTemplate.postForEntity("/auth/login", goodRequest, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.valueOf(423));
    }
}
