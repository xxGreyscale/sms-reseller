package com.opendesk.identity.admin;

import com.opendesk.identity.AbstractIntegrationTest;
import com.opendesk.identity.user.User;
import com.opendesk.identity.user.UserRepository;
import com.opendesk.identity.user.UserRole;
import com.opendesk.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static com.opendesk.identity.admin.AdminLoginIT.ADMIN_EMAIL;
import static com.opendesk.identity.admin.AdminLoginIT.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADMN-02: Admin user search integration test.
 *
 * <p>RED → GREEN by plan 05-03 (Task 2).
 * Verifies GET /api/v1/admin/users?q={term} with ROLE_ADMIN JWT returns paged UserSummaryDto results.
 * Verifies a ROLE_USER token is rejected with 403.
 */
class AdminUserSearchIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Seed admin user
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(ADMIN_EMAIL)
                .phone(null)
                .fullName("Platform Admin")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(UserRole.ADMIN)
                .status(VerificationStatus.VERIFIED)
                .build());

        // Seed a regular user to search for
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .phone("+255700000010")
                .fullName("Alice Mwangi")
                .passwordHash(passwordEncoder.encode("Pass1234!"))
                .role(UserRole.USER)
                .status(VerificationStatus.VERIFIED)
                .build());

        // Seed another regular user to test phone search
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("bob@example.com")
                .phone("+255700000020")
                .fullName("Bob Kamau")
                .passwordHash(passwordEncoder.encode("Pass1234!"))
                .role(UserRole.USER)
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build());
    }

    /** Logs in as admin and returns the Bearer token. */
    private String getAdminToken() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/api/v1/auth/admin/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    /** Logs in as a regular user and returns the Bearer token. */
    private String getRegularUserToken() {
        // Register + login flow is complex; instead, use the existing LoginService via /auth/login.
        // But the user in setUp has no deviceId support needed. Use admin login endpoint with
        // a user that has role=USER — we manually call /auth/login which returns ROLE_USER token.
        // Since LoginIT tests cover /auth/login separately, here we just need a ROLE_USER JWT.
        // Simplest approach: call /auth/login for alice.
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                Map.of("email", "alice@example.com", "password", "Pass1234!", "deviceId", "test-device"),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    @Test
    void adminCanSearchUsersByEmail() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/users?q=alice@example.com",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("alice@example.com");
        assertThat(response.getBody()).contains("content");
        // UserSummaryDto must not expose password
        assertThat(response.getBody()).doesNotContain("passwordHash").doesNotContain("password_hash");
    }

    @Test
    void adminCanSearchUsersByPhone() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/users?q=255700000020",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("bob@example.com");
    }

    @Test
    void regularUserTokenReturns403() {
        String userToken = getRegularUserToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/users?q=alice",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminSearchWithNoQueryReturnsAllUsers() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("content");
    }
}
