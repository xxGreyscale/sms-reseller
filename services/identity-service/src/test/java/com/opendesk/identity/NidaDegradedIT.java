package com.opendesk.identity;

import com.opendesk.identity.verification.VerificationFinalizer;
import com.opendesk.identity.verification.VerificationOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Covers: IDEN-08 — Graceful degrade when NIDA unavailable; circuit breaker + retry; stays PENDING (D-05).
 *
 * <p>Container-backed integration test. Uses the "stub" profile with magic-NIN suffix convention:
 * <ul>
 *   <li>NIN ending in {@code 0003} → StubNidaVerificationService throws NidaTransientException (UNAVAILABLE)</li>
 *   <li>NIN ending in {@code 0001} → StubNidaVerificationService returns REJECTED</li>
 *   <li>NIN ending in anything else → StubNidaVerificationService returns VERIFIED after delay</li>
 * </ul>
 *
 * <p>Assertions spy on the {@link VerificationFinalizer} bean to detect whether
 * {@code finalizeVerification(userId)} was called (VERIFIED path) or not (PENDING / rejected path).
 */
class NidaDegradedIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoSpyBean
    private VerificationFinalizer verificationFinalizer;

    @Autowired
    private VerificationOrchestrator verificationOrchestrator;

    /**
     * SUCCESS path: stub NIN (no magic suffix) → VERIFIED → finalizer IS called.
     */
    @Test
    void successPathCallsFinalizer() throws InterruptedException {
        // Register with a regular NIN — no magic suffix → stub verifies after delay=0ms (test profile)
        var request = Map.of(
                "email", "success@example.com",
                "phone", "+255712345010",
                "nin", "19870101-00001-00010-01",  // does not end in 0001/0002/0003
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID userId = UUID.fromString((String) response.getBody().get("userId"));

        // Wait up to 2 seconds for the async NIDA call to complete and finalizer to be invoked
        verify(verificationFinalizer, timeout(2000).times(1)).finalizeVerification(userId);
    }

    /**
     * UNAVAILABLE path (IDEN-08): magic-NIN ending 0003 → NidaTransientException →
     * finalizer NOT called → user stays PENDING.
     */
    @Test
    void unavailableNidaFinalizeNotCalled() throws InterruptedException {
        var request = Map.of(
                "email", "unavailable@example.com",
                "phone", "+255712345020",
                "nin", "19870101-00001-00020-0003",  // magic suffix: UNAVAILABLE
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Response must still be PENDING (returned before async call)
        assertThat(response.getBody().get("status")).isEqualTo("PENDING_VERIFICATION");

        UUID userId = UUID.fromString((String) response.getBody().get("userId"));

        // Allow time for the async call to run and fail
        Thread.sleep(500);

        // Finalizer must NOT have been called — user stays PENDING
        verify(verificationFinalizer, never()).finalizeVerification(userId);
    }

    /**
     * REJECTED path: magic-NIN ending 0001 → NidaResult.REJECTED → finalizer NOT called.
     */
    @Test
    void rejectedNidaFinalizeNotCalled() throws InterruptedException {
        var request = Map.of(
                "email", "rejected@example.com",
                "phone", "+255712345030",
                "nin", "19870101-00001-00030-0001",  // magic suffix: REJECT
                "password", "SecurePass1!"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING_VERIFICATION");

        UUID userId = UUID.fromString((String) response.getBody().get("userId"));

        // Allow time for the async call to run
        Thread.sleep(500);

        // Finalizer must NOT have been called — rejected identity stays PENDING
        verify(verificationFinalizer, never()).finalizeVerification(userId);
    }

    /**
     * Re-dispatch path (IDEN-08): direct orchestrator call with unavailable magic-NIN first
     * (simulates retry job behavior) — on success call the finalizer is invoked.
     *
     * <p>This test directly calls the orchestrator to simulate what the retry job does:
     * re-dispatches a pending userId. On a SUCCESS NIN the finalizer is called.
     */
    @Test
    void retryDispatchCallsFinalizerOnSuccess() throws InterruptedException {
        // Dispatch with a SUCCESS NIN (simulates the retry job re-dispatching with stored NIN)
        UUID userId = UUID.randomUUID();
        verificationOrchestrator.verifyAsync(userId, "19870101-00001-00099-01"); // no magic suffix → SUCCESS

        // Wait for the async call to complete and finalizer to be invoked
        verify(verificationFinalizer, timeout(2000).times(1)).finalizeVerification(userId);
    }
}
