package com.opendesk.messaging;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.context.TestComponent;

import java.util.Date;
import java.util.List;

/**
 * Test helper that mints RSA-signed JWTs for messaging-service integration tests.
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

    public String createToken(String userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("https://identity.open-desk")
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

    public String createAdminToken(String userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issuer("https://identity.open-desk")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 15 * 60 * 1000L))
                    .claim("roles", List.of("ROLE_USER", "ROLE_ADMIN"))
                    .claim("verification_status", "VERIFIED")
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims
            );
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create admin test JWT for userId=" + userId, e);
        }
    }
}
