package com.opendesk.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers: D-02 — Cross-module JWT contract: token signed with RSA private key (identity-service)
 * is correctly validated with the matching RSA public key (shared-security / all modules).
 *
 * <p>This test proves the "identity issues, all others only validate" contract without needing
 * any Spring application context. It uses the shared test keypair from test-keys/ — the same
 * files present in identity-service's test resources — to simulate the prod key split.
 *
 * <p>Threat model: T-02-10 — cross-module JWT contract (mitigate disposition).
 */
class JwtValidationUnitTest {

    @Test
    void signedTokenWithVerificationStatusClaimIsDecodedByPublicKey() throws Exception {
        // Arrange: load shared RSA test keypair (mirrors what TestKeys.java does in identity-service)
        RSAPrivateKey privateKey = loadPrivateKey();
        RSAPublicKey publicKey = loadPublicKey();

        // Sign a JWT with verification_status claim using the RSA private key (identity-service role)
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-123")
                .claim("verification_status", "PENDING_VERIFICATION")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
        );
        signedJWT.sign(new RSASSASigner(privateKey));
        String tokenValue = signedJWT.serialize();

        // Act: decode using NimbusJwtDecoder.withPublicKey (shared-security / all-modules role)
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        var decoded = decoder.decode(tokenValue);

        // Assert: verification_status claim is readable — proves cross-module contract (D-02)
        assertThat(decoded.getSubject()).isEqualTo("user-123");
        assertThat((String) decoded.getClaim("verification_status")).isEqualTo("PENDING_VERIFICATION");
    }

    // --- Key loading helpers (mirrors TestKeys.java in identity-service test sources) ---

    private static RSAPrivateKey loadPrivateKey() throws Exception {
        String pem = readPemResource("/test-keys/jwt-private.pem");
        byte[] der = decodePem(pem, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey loadPublicKey() throws Exception {
        String pem = readPemResource("/test-keys/jwt-public.pem");
        byte[] der = decodePem(pem, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private static String readPemResource(String resource) throws Exception {
        try (InputStream is = JwtValidationUnitTest.class.getResourceAsStream(resource)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + resource);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] decodePem(String pem, String label) {
        String stripped = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}
