package com.smsreseller.identity.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Configurable-outcome stub for NIDA identity verification (D-04/D-05).
 *
 * <p>Active only under the {@code stub} Spring profile (local dev, test environments).
 * Supports two outcome-selection mechanisms:
 *
 * <ol>
 *   <li><strong>Magic-NIN suffix</strong> (D-05): the last 4 digits of the NIN determine outcome:
 *       <ul>
 *         <li>{@code ...0001} → REJECTED</li>
 *         <li>{@code ...0002} → TIMEOUT (throws {@link NidaTransientException})</li>
 *         <li>{@code ...0003} → UNAVAILABLE (throws {@link NidaTransientException})</li>
 *         <li>anything else  → SUCCESS (returns {@link NidaResult#VERIFIED})</li>
 *       </ul>
 *   </li>
 *   <li><strong>Property override</strong>: set {@code app.nida.stub.default-outcome} to
 *       {@code SUCCESS}, {@code REJECT}, {@code TIMEOUT}, or {@code UNAVAILABLE} to
 *       force a specific outcome for all NINs (overrides the magic-NIN suffix).</li>
 * </ol>
 *
 * <p>PII: the NIN MUST NOT be logged. The stub logs only the outcome and the NIN length.
 */
@Profile("stub")
@Service
public class StubNidaVerificationService implements NidaVerificationService {

    private static final Logger log = LoggerFactory.getLogger(StubNidaVerificationService.class);

    /**
     * Simulated delay in milliseconds before returning a result.
     * Set to 0 in application-test.yml for fast tests; 3000ms in application.yml for realistic dev.
     */
    @Value("${app.nida.stub.auto-verify-delay-ms:3000}")
    private long autoVerifyDelayMs;

    /**
     * Default outcome when no magic-NIN suffix matches.
     * Values: SUCCESS (default), REJECT, TIMEOUT, UNAVAILABLE.
     */
    @Value("${app.nida.stub.default-outcome:SUCCESS}")
    private String defaultOutcome;

    @Override
    public NidaResult verify(String nin) {
        // Determine outcome — PII note: log only the outcome, never the NIN itself
        String outcome = resolveOutcome(nin);
        log.debug("Stub NIDA verify — nin length={}, resolved outcome={}", nin == null ? 0 : nin.length(), outcome);

        // Simulate network latency
        if (autoVerifyDelayMs > 0) {
            try {
                Thread.sleep(autoVerifyDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NidaTransientException("Stub interrupted during delay", e);
            }
        }

        return switch (outcome) {
            case "REJECT" -> NidaResult.REJECTED;
            case "TIMEOUT" -> throw new NidaTransientException("Stub: simulated NIDA timeout");
            case "UNAVAILABLE" -> throw new NidaTransientException("Stub: simulated NIDA unavailable");
            default -> NidaResult.VERIFIED;
        };
    }

    /**
     * Resolve the outcome for the given NIN.
     *
     * <p>Magic-NIN suffix takes priority unless the property override is set to something
     * other than the default {@code SUCCESS}. This allows integration tests to force
     * specific outcomes via magic-NIN without touching properties.
     */
    private String resolveOutcome(String nin) {
        if (nin != null && nin.length() >= 4) {
            String suffix = nin.substring(nin.length() - 4);
            switch (suffix) {
                case "0001" -> { return "REJECT"; }
                case "0002" -> { return "TIMEOUT"; }
                case "0003" -> { return "UNAVAILABLE"; }
            }
        }
        // Fall back to property-configured default
        return defaultOutcome;
    }
}
