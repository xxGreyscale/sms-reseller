package com.smsreseller.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT configuration for the identity-service — the sole JWT issuer on the platform.
 *
 * <p>Security contract (CLAUDE.md + D-02):
 * <ul>
 *   <li>Identity holds BOTH private + public key halves and exposes a {@link JwtEncoder}.</li>
 *   <li>All other modules receive ONLY the public key via
 *       {@code spring.security.oauth2.resourceserver.jwt.public-key-location} and use
 *       {@link NimbusJwtDecoder#withPublicKey} from shared-security's {@code JwtConfig}.</li>
 *   <li>The RSA private key is NEVER logged (T-02-V6). Only the keyID is safe to log.</li>
 * </ul>
 *
 * <p>Config keys (application.yml / K8s Secret):
 * <ul>
 *   <li>{@code app.jwt.private-key-location} — PKCS#8 PEM path (classpath or file:)</li>
 *   <li>{@code app.jwt.public-key-location}  — X.509/SPKI PEM path</li>
 * </ul>
 */
@Slf4j
@Configuration
public class JwtConfig {

    private static final String KEY_ID = "identity-1";

    @Value("${app.jwt.private-key-location}")
    private Resource privateKeyLocation;

    @Value("${app.jwt.public-key-location}")
    private Resource publicKeyLocation;

    @Bean
    public RSAPrivateKey rsaPrivateKey() throws Exception {
        String pem = readResource(privateKeyLocation);
        byte[] der = decodePem(pem, "PRIVATE KEY");
        // SECURITY: private key material — never pass to logger (T-02-V6)
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        String pem = readResource(publicKeyLocation);
        byte[] der = decodePem(pem, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    /**
     * JWT encoder bean — identity-service signs access tokens with RSA-2048 (RS256).
     * Key ID "identity-1" is embedded in the JWT header to support future key rotation.
     */
    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        log.info("Configuring JwtEncoder with keyID={}", KEY_ID);
        RSAKey jwk = new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .keyID(KEY_ID)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /**
     * JWT decoder bean for the identity-service itself (validates tokens it issued).
     * Downstream modules should use the shared-security JwtConfig bean instead.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
    }

    // --- Helpers ---

    private static String readResource(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
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
