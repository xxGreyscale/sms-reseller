package com.opendesk.identity;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Covers: IDEN-05 — JWT issuance with RSA private key (NimbusJwtEncoder); verification_status claim (D-02).
 *
 * <p>Placeholder stub for the identity-side JWT issuer unit test.
 * Rewritten in plan 02-02 once JwtConfig/JwtIssuerService are implemented.
 * The cross-module encode→decode contract is proven NOW in JwtValidationUnitTest (shared-security).
 */
class JwtIssuerUnitTest {

    @Test
    void issuesJwtWithVerificationStatusClaim() {
        Assumptions.abort("pending impl: IDEN-05 JWT issuer (plan 02-02)");
    }
}
