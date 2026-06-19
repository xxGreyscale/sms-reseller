package com.opendesk.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Covers: IDEN-04 — Log in with email + password.
 *
 * <p>Tests valid login, invalid password, and unknown email (no enumeration).
 */
class LoginIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL = "login-test@example.com";
    private static final String PASSWORD = "S3cur3Pass!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .phone("+255700000001")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build();
        userRepository.save(user);
    }

    @Test
    void returnsAccessAndRefreshTokens() throws Exception {
        var request = Map.of("email", EMAIL, "password", PASSWORD, "deviceId", "device-1");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void verificationStatusClaimMatchesDbStatus() throws Exception {
        var request = Map.of("email", EMAIL, "password", PASSWORD, "deviceId", "device-2");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        String accessToken = body.get("accessToken").asText();

        // Decode the JWT payload (base64url second segment)
        String[] parts = accessToken.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);
        assertThat(claims.get("verification_status").asText()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void invalidPasswordReturns401WithGenericMessage() {
        var request = Map.of("email", EMAIL, "password", "wrong-password", "deviceId", "device-3");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownEmailReturns401WithSameMessageAsWrongPassword() {
        var wrongPassword = Map.of("email", EMAIL, "password", "wrong!", "deviceId", "d1");
        var unknownEmail = Map.of("email", "nobody@example.com", "password", "any", "deviceId", "d2");

        ResponseEntity<String> r1 = restTemplate.postForEntity("/auth/login", wrongPassword, String.class);
        ResponseEntity<String> r2 = restTemplate.postForEntity("/auth/login", unknownEmail, String.class);

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // both must return identical status — no account enumeration (T-02-ENUM)
        assertThat(r1.getStatusCode()).isEqualTo(r2.getStatusCode());
    }
}
