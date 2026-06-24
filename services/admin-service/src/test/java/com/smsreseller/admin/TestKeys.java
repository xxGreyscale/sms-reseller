package com.smsreseller.admin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Shared RSA-2048 test keypair loader for admin-service tests.
 *
 * <p>These are TEST-ONLY keys committed to src/test/resources/test-keys.
 * They MUST NEVER be reused in production. Production keys are injected via
 * Kubernetes Secrets (INFR-05).
 */
public final class TestKeys {

    private static final String PRIVATE_KEY_RESOURCE = "/test-keys/jwt-private.pem";
    private static final String PUBLIC_KEY_RESOURCE = "/test-keys/jwt-public.pem";

    private TestKeys() {}

    /** Loads the RSA-2048 private key (PKCS#8 PEM) for JWT signing in tests. */
    public static RSAPrivateKey loadPrivateKey() {
        try {
            String pem = readPemResource(PRIVATE_KEY_RESOURCE);
            byte[] der = decodePem(pem, "PRIVATE KEY");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load test RSA private key from " + PRIVATE_KEY_RESOURCE, e);
        }
    }

    /** Loads the RSA-2048 public key (X.509/SPKI PEM) for JWT verification in tests. */
    public static RSAPublicKey loadPublicKey() {
        try {
            String pem = readPemResource(PUBLIC_KEY_RESOURCE);
            byte[] der = decodePem(pem, "PUBLIC KEY");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load test RSA public key from " + PUBLIC_KEY_RESOURCE, e);
        }
    }

    private static String readPemResource(String resourcePath) throws Exception {
        try (InputStream is = TestKeys.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] decodePem(String pem, String label) {
        String header = "-----BEGIN " + label + "-----";
        String footer = "-----END " + label + "-----";
        String stripped = pem
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}
