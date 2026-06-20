package com.opendesk.payment;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test-only JWT generator for payment-service integration tests.
 *
 * <p>Mirrors the JwtIssuer from identity-service using the shared test RSA keypair.
 * Issues tokens signed with the same private key that shared-security validates via
 * the public-key-location in application-test.yml.
 */
public class JwtTestHelper {

    private static final String ISSUER = "https://identity.open-desk";

    private final JwtEncoder jwtEncoder;

    public JwtTestHelper() {
        RSAPrivateKey privateKey = TestKeys.loadPrivateKey();
        RSAPublicKey publicKey = TestKeys.loadPublicKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("identity-1")
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    /**
     * Generates a signed JWT for the given user ID.
     *
     * @param userId user UUID string (becomes JWT subject)
     * @return compact JWT string
     */
    public String generateToken(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(15 * 60))
                .claim("verification_status", "VERIFIED")
                .claim("roles", List.of("ROLE_USER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Generates a signed JWT for a random user (convenience method).
     */
    public String generateToken() {
        return generateToken(UUID.randomUUID().toString());
    }
}
