package com.smsreseller.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Shared JWT decoder configuration — imported by all 8 downstream modules.
 *
 * <p>Architecture contract (CLAUDE.md JWT pattern):
 * <ul>
 *   <li>Identity-service holds the RSA private key and signs access tokens ({@code NimbusJwtEncoder}).</li>
 *   <li>All other modules hold ONLY the RSA public key and validate with {@code NimbusJwtDecoder.withPublicKey(...)}.</li>
 *   <li>This asymmetric split means downstream modules can NEVER forge tokens (T-02-05 mitigated).</li>
 * </ul>
 *
 * <p>Config key (K8s ConfigMap / Secret):
 * <pre>
 *   spring.security.oauth2.resourceserver.jwt.public-key-location: classpath:keys/jwt-public.pem
 * </pre>
 *
 * <p>Each module's {@code application.yml} (or K8s overlay) must set this property.
 * The public key is injected at deploy time — no JWKS endpoint is used at MVP (A1).
 *
 * <p>This {@code @Configuration} class is auto-detected when any module depends on
 * {@code :libs:shared-security}. To use:
 * <ol>
 *   <li>Add {@code implementation(project(":libs:shared-security"))} to the module's build.</li>
 *   <li>Configure {@code spring.security.oauth2.resourceserver.jwt.public-key-location}.</li>
 *   <li>The {@link JwtDecoder} bean is available for injection.</li>
 * </ol>
 */
@Configuration
public class JwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}")
    private Resource publicKeyLocation;

    /**
     * JWT decoder bean — validates tokens signed by identity-service's RSA private key.
     * Shared by all 8 downstream modules; imported via {@code :libs:shared-security}.
     */
    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        RSAPublicKey publicKey = loadPublicKey(publicKeyLocation);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private static RSAPublicKey loadPublicKey(Resource resource) throws Exception {
        String pem;
        try (InputStream is = resource.getInputStream()) {
            pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(stripped);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }
}
