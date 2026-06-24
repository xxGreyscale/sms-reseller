package com.smsreseller.contact;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.context.TestComponent;

import java.util.Date;
import java.util.List;

/**
 * Test helper that mints RSA-signed JWTs for contact-service integration tests.
 *
 * <p>Uses the same RSA-2048 keypair stored in {@code src/test/resources/test-keys/}.
 * The contact-service SecurityConfig points its JWT decoder to the same public key,
 * so tokens created here will pass validation in a running test context.
 */
@TestComponent
public class JwtTestHelper {

    private final RSASSASigner signer;

    public JwtTestHelper() {
        try {
            this.signer = new RSASSASigner(TestKeys.loadPrivateKey());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise JWT test signer", e);
        }
    }

    /**
     * Creates a short-lived (15 min) RSA-signed JWT with the given subject (userId).
     *
     * @param userId the UUID string to set as the JWT subject
     * @return signed compact JWT string (Bearer-ready)
     */
    public String createToken(String userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("https://identity.sms-reseller")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 15 * 60 * 1000L))
                    .claim("roles", List.of("ROLE_USER"))
                    .claim("verification_status", "VERIFIED")
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims
            );
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test JWT for userId=" + userId, e);
        }
    }
}
