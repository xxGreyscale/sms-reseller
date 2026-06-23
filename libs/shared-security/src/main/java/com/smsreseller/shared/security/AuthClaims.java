package com.smsreseller.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Static accessors for sms-reseller custom JWT claims.
 *
 * <p>All downstream modules (catalog, wallet, payment, contact, messaging, notification, admin)
 * import this class to gate features on the {@code verification_status} claim (D-02) without
 * making a runtime call to identity-service.
 *
 * <p>Claim contract:
 * <ul>
 *   <li>{@code verification_status} — string matching a {@link VerificationStatus} literal</li>
 *   <li>{@code roles} — list of Spring authority strings (e.g. {@code ["ROLE_USER"]})</li>
 * </ul>
 *
 * <p>Usage example in a service:
 * <pre>{@code
 *   @GetMapping("/send")
 *   public ResponseEntity<?> send(JwtAuthenticationToken auth) {
 *       Jwt jwt = auth.getToken();
 *       if (!AuthClaims.isVerified(jwt)) {
 *           return ResponseEntity.status(403).build();
 *       }
 *       // proceed...
 *   }
 * }</pre>
 */
public final class AuthClaims {

    /** JWT claim key for the NIDA verification status (D-02). */
    public static final String VERIFICATION_STATUS = "verification_status";

    /** JWT claim key for the user's roles list. */
    public static final String ROLES = "roles";

    private AuthClaims() {}

    /**
     * Returns {@code true} if the JWT carries a {@code verification_status} claim equal to
     * {@link VerificationStatus#VERIFIED}. Returns {@code false} for any other value or if
     * the claim is missing.
     *
     * @param jwt decoded and validated JWT (Spring Security {@link Jwt})
     */
    public static boolean isVerified(Jwt jwt) {
        return VerificationStatus.VERIFIED == getVerificationStatus(jwt);
    }

    /**
     * Parses the {@code verification_status} claim from the JWT.
     *
     * @param jwt decoded and validated JWT
     * @return parsed {@link VerificationStatus}, or {@link VerificationStatus#PENDING_VERIFICATION}
     *         if the claim is missing or unrecognized (fail-safe: treat unknown as not verified)
     */
    public static VerificationStatus getVerificationStatus(Jwt jwt) {
        String raw = jwt.getClaimAsString(VERIFICATION_STATUS);
        if (raw == null) {
            return VerificationStatus.PENDING_VERIFICATION;
        }
        try {
            return VerificationStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // Unknown status value — fail safe: deny access
            return VerificationStatus.PENDING_VERIFICATION;
        }
    }
}
