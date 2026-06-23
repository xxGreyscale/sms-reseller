package com.smsreseller.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: IDEN-01 — Register with phone number + email.
 *         IDEN-02 — Registration returns PENDING immediately (async NIDA dispatch).
 *
 * <p>Container-backed integration test — runs against real Postgres 16 + Redis 7
 * (via AbstractIntegrationTest). Profile "stub" activates StubNidaVerificationService.
 * Stub delay is 0ms in application-test.yml so tests run fast.
 */
class RegistrationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsImmediatelyWithPendingStatus() {
        // Arrange
        var request = Map.of(
                "email", "alice@example.com",
                "phone", "+255712345001",
                "nin", "19870101-00001-00001-01",
                "password", "SecurePass1!"
        );

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/register", request, Map.class);

        // Assert — must return 200 with PENDING_VERIFICATION status (IDEN-01/02)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.getBody().get("userId")).isNotNull();
        assertThat(response.getBody().get("accessToken")).isNotNull();
        // password must never appear in response
        assertThat(response.getBody()).doesNotContainKey("password");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");
    }

    @Test
    void duplicateEmailReturns409() {
        var request = Map.of(
                "email", "bob@example.com",
                "phone", "+255712345002",
                "nin", "19870101-00001-00002-01",
                "password", "SecurePass1!"
        );
        restTemplate.postForEntity("/auth/register", request, Map.class);

        var duplicate = Map.of(
                "email", "bob@example.com",
                "phone", "+255712345099",
                "nin", "19870101-00001-00099-01",
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", duplicate, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicatePhoneReturns409() {
        var request = Map.of(
                "email", "charlie1@example.com",
                "phone", "+255712345003",
                "nin", "19870101-00001-00003-01",
                "password", "SecurePass1!"
        );
        restTemplate.postForEntity("/auth/register", request, Map.class);

        var duplicate = Map.of(
                "email", "charlie2@example.com",
                "phone", "+255712345003",
                "nin", "19870101-00001-00004-01",
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", duplicate, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidEmailReturns400() {
        var request = Map.of(
                "email", "not-an-email",
                "phone", "+255712345005",
                "nin", "19870101-00001-00005-01",
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shortPasswordReturns400() {
        var request = Map.of(
                "email", "dave@example.com",
                "phone", "+255712345006",
                "nin", "19870101-00001-00006-01",
                "password", "short"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
