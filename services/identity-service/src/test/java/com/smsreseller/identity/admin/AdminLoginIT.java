package com.smsreseller.identity.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import com.smsreseller.identity.AbstractIntegrationTest;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.UserRole;
import com.smsreseller.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADMN-01: Admin login integration test.
 *
 * <p>RED → GREEN by plan 05-03 (Task 1).
 * Verifies POST /api/v1/auth/admin/login with seeded admin credentials returns 200 + JWT
 * carrying roles:[ROLE_ADMIN] with ~60-minute TTL.
 *
 * <p>Flyway is disabled in tests (application-test.yml: flyway.enabled=false, ddl-auto=create-drop).
 * The admin user is inserted directly via UserRepository in @BeforeEach.
 */
class AdminLoginIT extends AbstractIntegrationTest {

    static final String ADMIN_EMAIL = "admin@sms-reseller.app";
    static final String ADMIN_PASSWORD = "Admin1234!";

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        // Seed admin user with role=ADMIN, verification_status=VERIFIED.
        // In production this is done via V5 Flyway migration with env placeholders (D-02).
        User admin = User.builder()
                .id(UUID.randomUUID())
                .email(ADMIN_EMAIL)
                .phone(null)  // admin account has no phone
                .fullName("Platform Admin")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(UserRole.ADMIN)
                .status(VerificationStatus.VERIFIED)
                .build();
        userRepository.save(admin);

        // Seed a regular user to test role rejection
        User regularUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .phone("+255700000001")
                .fullName("Regular User")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(UserRole.USER)
                .status(VerificationStatus.VERIFIED)
                .build();
        userRepository.save(regularUser);
    }

    @Test
    void adminLoginWithValidCredentialsReturnsJwtWithAdminRole() throws Exception {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/admin/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");

        String token = (String) response.getBody().get("accessToken");
        var parsed = JWTParser.parse(token);
        var claims = parsed.getJWTClaimsSet();

        // roles claim must contain ROLE_ADMIN
        List<String> roles = claims.getStringListClaim("roles");
        assertThat(roles).containsExactly("ROLE_ADMIN");

        // TTL should be approximately 60 minutes (within ±2 minutes tolerance)
        long expiresIn = claims.getExpirationTime().toInstant().getEpochSecond()
                - claims.getIssueTime().toInstant().getEpochSecond();
        assertThat(expiresIn).isBetween(3480L, 3720L); // 58–62 minutes
    }

    @Test
    void adminLoginWithWrongPasswordReturns401() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/admin/login",
                Map.of("email", ADMIN_EMAIL, "password", "WrongPassword!"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminLoginWithNonAdminUserReturns401() {
        // A regular user attempting admin login must be rejected (role=USER, not ADMIN)
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/admin/login",
                Map.of("email", "user@example.com", "password", ADMIN_PASSWORD),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
