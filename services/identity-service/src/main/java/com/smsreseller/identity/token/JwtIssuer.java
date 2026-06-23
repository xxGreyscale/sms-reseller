package com.smsreseller.identity.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.smsreseller.identity.user.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues RSA-signed (RS256) access JWTs for the sms-reseller platform.
 *
 * <p>Contract (IDEN-05 + D-02):
 * <ul>
 *   <li>Subject: user UUID as string</li>
 *   <li>Issuer: {@code https://identity.sms-reseller}</li>
 *   <li>Expiry: now + 15 minutes (D-06)</li>
 *   <li>Claim {@code verification_status}: {@link VerificationStatus#name()} — D-02 load-bearing claim</li>
 *   <li>Claim {@code roles}: {@code ["ROLE_USER"]}</li>
 * </ul>
 *
 * <p>Security: the RSA private key is loaded by {@code JwtConfig} and injected here.
 * It is never logged or exposed outside this component (T-02-V6).
 */
@Component
@RequiredArgsConstructor
public class JwtIssuer {

    static final String ISSUER = "https://identity.sms-reseller";

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.access-token-ttl-minutes:15}")
    private long accessTokenTtlMinutes;

    /**
     * Issues a signed access JWT for the given user with the given verification status.
     *
     * @param userId user UUID (becomes JWT subject)
     * @param status NIDA verification status — embedded as {@code verification_status} claim (D-02)
     * @return compact JWT string ready to send to the client
     */
    public String issueAccessToken(UUID userId, VerificationStatus status) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES))
                .claim("verification_status", status.name())
                .claim("roles", List.of("ROLE_USER"))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Value("${app.jwt.admin-token-ttl-minutes:60}")
    private long adminTokenTtlMinutes;

    /**
     * Issues a signed admin JWT for the given admin user (ADMN-01, D-02).
     *
     * <p>Claims: roles:[ROLE_ADMIN], no verification_status claim (admin is always VERIFIED).
     * TTL: 60 minutes (RESEARCH.md Pitfall 6 — no refresh for admin tokens at MVP).
     *
     * @param adminId UUID of the admin user (becomes JWT subject)
     * @return compact JWT string ready to send to the admin-web client
     */
    public String issueAdminToken(UUID adminId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(adminId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(adminTokenTtlMinutes, ChronoUnit.MINUTES))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Factory method for unit tests — builds a standalone {@link JwtIssuer} with a
     * default 15-minute TTL using the provided test keys, without a Spring context.
     *
     * <p>NOT for use in production code — use the Spring bean instead.
     */
    public static JwtIssuer withKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("test-identity-1")
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        JwtIssuer issuer = new JwtIssuer(encoder);
        issuer.accessTokenTtlMinutes = 15;
        return issuer;
    }
}
