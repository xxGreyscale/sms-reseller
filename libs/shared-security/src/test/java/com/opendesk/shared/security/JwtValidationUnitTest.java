package com.opendesk.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers: D-02 — Cross-module JWT contract: token signed with RSA private key (identity-service)
 * is correctly validated with the matching RSA public key (shared-security / all modules).
 *
 * <p>Also covers: T-02-05 — JWT forgery by downstream modules must be REJECTED.
 *
 * <p>This test proves the "identity issues, all others only validate" contract without needing
 * any Spring application context. It uses the shared test keypair from test-keys/ — the same
 * files present in identity-service's test resources — to simulate the prod key split.
 *
 * <p>Threat model: T-02-05 (forgery rejection), T-02-10 (cross-module JWT contract).
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

    /**
     * T-02-05: A token signed with a FOREIGN RSA key (not the identity-service key)
     * MUST be rejected by NimbusJwtDecoder.withPublicKey(identityPublicKey).
     *
     * <p>This proves that downstream modules holding only the public key cannot forge tokens —
     * only identity-service (holding the private key) can issue valid access tokens.
     */
    @Test
    void tokenSignedWithForeignKeyIsRejectedByDecoder() throws Exception {
        // Generate a completely different RSA key pair (simulates an attacker's keys)
        RSAPublicKey legitimatePublicKey = loadPublicKey();

        var foreignKeyPair = KeyPairGenerator.getInstance("RSA");
        foreignKeyPair.initialize(2048);
        var kp = foreignKeyPair.generateKeyPair();
        RSAPrivateKey foreignPrivateKey = (RSAPrivateKey) kp.getPrivate();

        // Sign a JWT with the FOREIGN private key (attacker attempting to forge a VERIFIED token)
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("attacker-user")
                .claim("verification_status", "VERIFIED")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        SignedJWT forgedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
        );
        forgedJWT.sign(new RSASSASigner(foreignPrivateKey));
        String forgedToken = forgedJWT.serialize();

        // Act + Assert: decoder with the LEGITIMATE public key MUST reject the forged token
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(legitimatePublicKey).build();
        assertThatThrownBy(() -> decoder.decode(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    /**
     * AuthClaims.isVerified returns true only when claim value is VERIFIED.
     */
    @Test
    void authClaimsIsVerifiedReturnsTrueOnlyForVerifiedStatus() throws Exception {
        RSAPrivateKey privateKey = loadPrivateKey();
        RSAPublicKey publicKey = loadPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        // Token with VERIFIED status
        Jwt verifiedJwt = decoder.decode(buildToken(privateKey, "VERIFIED"));
        assertThat(AuthClaims.isVerified(verifiedJwt)).isTrue();

        // Token with PENDING_VERIFICATION status
        Jwt pendingJwt = decoder.decode(buildToken(privateKey, "PENDING_VERIFICATION"));
        assertThat(AuthClaims.isVerified(pendingJwt)).isFalse();
    }

    @Test
    void authClaimsGetVerificationStatusReturnsMappedEnum() throws Exception {
        RSAPrivateKey privateKey = loadPrivateKey();
        RSAPublicKey publicKey = loadPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        Jwt verifiedJwt = decoder.decode(buildToken(privateKey, "VERIFIED"));
        assertThat(AuthClaims.getVerificationStatus(verifiedJwt))
                .isEqualTo(VerificationStatus.VERIFIED);

        Jwt pendingJwt = decoder.decode(buildToken(privateKey, "PENDING_VERIFICATION"));
        assertThat(AuthClaims.getVerificationStatus(pendingJwt))
                .isEqualTo(VerificationStatus.PENDING_VERIFICATION);
    }

    // --- Helpers ---

    private static String buildToken(RSAPrivateKey privateKey, String statusValue) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-test")
                .claim("verification_status", statusValue)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
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
