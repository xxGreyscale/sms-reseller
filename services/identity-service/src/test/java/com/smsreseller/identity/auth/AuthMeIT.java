package com.smsreseller.identity.auth;

// Requirement: MOBL-02 — D-13 GET /auth/me non-rotating verification-status read
// RED → made GREEN by plan 06-04 Task 1

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsreseller.identity.AbstractIntegrationTest;
import com.smsreseller.identity.token.RefreshTokenService;
import com.smsreseller.identity.user.User;
import com.smsreseller.identity.user.UserRepository;
import com.smsreseller.identity.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: GET /auth/me returns the caller's {userId, status} without rotating
 * the refresh token (D-13, MOBL-02).
 *
 * <p>Behaviors tested:
 * <ol>
 *   <li>Authenticated GET /auth/me → 200 {userId, status} matching DB; no new tokens.</li>
 *   <li>Unauthenticated GET /auth/me → 401.</li>
 *   <li>Status reflects DB truth: user saved as VERIFIED → /me returns "VERIFIED".</li>
 * </ol>
 *
 * <p>Non-rotation assertion: the Redis hash of the refresh token is identical before and
 * after two consecutive /me calls — proving /me never touches RefreshTokenService.
 */
class AuthMeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String EMAIL = "auth-me-test@example.com";
    private static final String PASSWORD = "S3cur3Pass!";
    private static final String DEVICE_ID = "device-me-1";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ── Test 1: authenticated GET /auth/me → 200 with {userId, status}; no token rotation ──

    @Test
    void authenticatedGetMe_returns200WithUserIdAndStatus_andDoesNotRotateRefreshToken() throws Exception {
        // GIVEN — a PENDING_VERIFICATION user saved to DB
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email(EMAIL)
                .phone("+255700000099")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(VerificationStatus.PENDING_VERIFICATION)
                .build();
        userRepository.save(user);

        // Obtain an access token via login
        String accessToken = login(EMAIL, PASSWORD, DEVICE_ID);

        // Read the Redis key for the refresh token BEFORE calling /auth/me
        String redisKey = "refresh:" + userId + ":" + DEVICE_ID;
        String storedHashBefore = stringRedisTemplate.opsForValue().get(redisKey);
        assertThat(storedHashBefore).as("refresh token must exist in Redis before /me calls").isNotNull();

        // WHEN — call /auth/me twice
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> resp1 = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ResponseEntity<String> resp2 = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // THEN — both calls return 200
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(resp1.getBody());
        assertThat(body.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(body.get("status").asText()).isEqualTo("PENDING_VERIFICATION");

        // No new tokens in the response body
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();

        // Non-rotation assertion: Redis value must be UNCHANGED after two /me calls
        String storedHashAfter = stringRedisTemplate.opsForValue().get(redisKey);
        assertThat(storedHashAfter)
                .as("GET /auth/me must NOT rotate the refresh token — Redis hash must be unchanged")
                .isEqualTo(storedHashBefore);
    }

    // ── Test 2: unauthenticated GET /auth/me → 401 ──

    @Test
    void unauthenticatedGetMe_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Test 3: status reflects DB truth (VERIFIED user → /me returns "VERIFIED") ──

    @Test
    void meReturnsCurrentDbStatus_verifiedUserReturnsVerified() throws Exception {
        // GIVEN — a VERIFIED user
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("verified-me@example.com")
                .phone("+255700000098")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(VerificationStatus.VERIFIED)
                .build();
        userRepository.save(user);

        // Login — the login service will issue tokens; access token still contains the subject
        String accessToken = login("verified-me@example.com", PASSWORD, "device-verified-1");

        // WHEN
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // THEN
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(body.get("status").asText())
                .as("GET /auth/me must re-read DB status — VERIFIED user must return VERIFIED")
                .isEqualTo("VERIFIED");
    }

    // ── Helpers ──

    private String login(String email, String password, String deviceId) throws Exception {
        var loginReq = Map.of("email", email, "password", password, "deviceId", deviceId);
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/login", loginReq, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(resp.getBody()).get("accessToken").asText();
    }
}
